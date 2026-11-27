package com.moewadeul.common.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogPersonalDataTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/fixtures/log-personal-data");

    private static final String LEVEL = "(?:trace|debug|info|warn|error)";
    private static final Pattern LOG_FIELD_CALL = Pattern.compile("\\blog\\s*\\.\\s*" + LEVEL + "\\s*\\(");
    private static final Pattern GET_LOGGER = Pattern.compile("\\bgetLogger\\s*\\(");
    private static final Pattern CHAINED_LEVEL = Pattern.compile("\\G\\s*\\.\\s*" + LEVEL + "\\s*\\(");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    // MailMessage.to()는 수신자 이메일이다. 소스만으로는 타입을 알 수 없어 인자 없는 to() 호출을 모두 막는다.
    private static final Pattern MAIL_RECIPIENT = Pattern.compile("\\.\\s*to\\s*\\(\\s*\\)");

    private static final Set<String> PERSONAL_WORDS = Set.of("email", "phone", "ci", "connecting");
    // demoIdentityKey는 데모 본인 확인에서 CI를 만드는 입력이라 CI와 같이 다룬다
    private static final List<String> DEMO_IDENTITY_KEY = List.of("demo", "identity", "key");

    // "파일#인자 식" → 넘겨도 되는 이유. 오탐이 생기면 여기에 이유와 함께 적는다.
    private static final Map<String, String> EXEMPTIONS = Map.of();

    private record Scan(int callCount, List<String> violations) {}

    @Test
    @DisplayName("[NFR-07] 로그 호출에 이메일·전화번호·연계정보 값을 넘기지 않는다")
    void logCallsDoNotReceivePersonalData() throws IOException {
        Scan scan = scan(MAIN_SOURCE_ROOT);

        assertThat(scan.callCount()).as("검사한 로그 호출 수").isPositive();
        assertThat(scan.violations()).as("로그 호출의 개인정보 인자").isEmpty();
    }

    @Test
    @DisplayName("[NFR-07] 가입 서비스가 command.email()을 로그에 넘기면 실패한다")
    void signupEmailLogFails() throws IOException {
        assertThat(scan(FIXTURE_ROOT.resolve("signup")).violations())
                .containsExactly("MemberSignupService.java.txt:7: log 호출에 개인정보 인자 command.email()을 넘긴다");
    }

    @Test
    @DisplayName("[NFR-07] 연계정보·그 해시·전화번호·메일 수신자를 넘기면 실패한다")
    void otherPersonalDataLogFails() throws IOException {
        assertThat(scan(FIXTURE_ROOT.resolve("personal")).violations())
                .containsExactlyInAnyOrder(
                        "Probe.java.txt:6: log 호출에 개인정보 인자 identity.ci()을 넘긴다",
                        "Probe.java.txt:10: log 호출에 개인정보 인자 ciHash을 넘긴다",
                        "Probe.java.txt:14: log 호출에 개인정보 인자 ciHasher.hash(ci)을 넘긴다",
                        "Probe.java.txt:18: log 호출에 개인정보 인자 demoIdentityKey을 넘긴다",
                        "Probe.java.txt:22: log 호출에 개인정보 인자 row.phone()을 넘긴다",
                        "Probe.java.txt:29: log 호출에 개인정보 인자 message.to()을 넘긴다");
    }

    @Test
    @DisplayName("[NFR-07] 문구에 email이 있어도 넘기는 값이 개인정보가 아니면 통과한다")
    void messageWordsAreNotChecked() throws IOException {
        Scan scan = scan(FIXTURE_ROOT.resolve("harmless"));

        assertThat(scan.callCount()).as("검사한 로그 호출 수").isEqualTo(5);
        assertThat(scan.violations()).isEmpty();
    }

    private static Scan scan(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(path -> {
                        String name = path.toString();
                        return name.endsWith(".java") || name.endsWith(".java.txt");
                    })
                    .sorted()
                    .toList();
        }
        int callCount = 0;
        List<String> violations = new ArrayList<>();
        for (Path path : paths) {
            String file = root.relativize(path).toString();
            String code = blankCommentsAndLiterals(Files.readString(path));
            for (int open : logCallOpenParens(code)) {
                callCount++;
                for (int[] range : arguments(code, open)) {
                    String argument = code.substring(range[0], range[1]).strip();
                    if (argument.isEmpty() || !isPersonal(argument)) {
                        continue;
                    }
                    String expression = argument.replaceAll("\\s+", " ");
                    if (EXEMPTIONS.containsKey(file + "#" + expression)) {
                        continue;
                    }
                    int start = range[0] + (code.substring(range[0], range[1]).indexOf(argument));
                    violations.add(file + ":" + lineOf(code, start) + ": log 호출에 개인정보 인자 " + expression + "을 넘긴다");
                }
            }
        }
        return new Scan(callCount, List.copyOf(violations));
    }

    private static List<Integer> logCallOpenParens(String code) {
        List<Integer> opens = new ArrayList<>();
        Matcher field = LOG_FIELD_CALL.matcher(code);
        while (field.find()) {
            opens.add(field.end() - 1);
        }
        Matcher getLogger = GET_LOGGER.matcher(code);
        while (getLogger.find()) {
            int close = matchingParen(code, getLogger.end() - 1);
            Matcher level = CHAINED_LEVEL.matcher(code);
            if (close > 0 && level.find(close + 1)) {
                opens.add(level.end() - 1);
            }
        }
        return opens;
    }

    private static List<int[]> arguments(String code, int open) {
        int close = matchingParen(code, open);
        List<int[]> ranges = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open + 1; i < close; i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                ranges.add(new int[] {start, i});
                start = i + 1;
            }
        }
        ranges.add(new int[] {start, close});
        return ranges;
    }

    private static int matchingParen(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("괄호 짝을 찾지 못했다: " + open);
    }

    private static boolean isPersonal(String argument) {
        if (MAIL_RECIPIENT.matcher(argument).find()) {
            return true;
        }
        Matcher identifier = IDENTIFIER.matcher(argument);
        while (identifier.find()) {
            List<String> words = words(identifier.group());
            if (words.stream().anyMatch(PERSONAL_WORDS::contains)
                    || Collections.indexOfSubList(words, DEMO_IDENTITY_KEY) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(String identifier) {
        return Arrays.stream(identifier.split("[_$]+"))
                .flatMap(part -> Arrays.stream(CAMEL_BOUNDARY.split(part)))
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static int lineOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // 주석과 문자열·문자 리터럴의 내용을 공백으로 바꾼다. 줄바꿈은 남겨 줄 번호를 지킨다.
    private static String blankCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(source, i, end, out);
                i = end;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = literalEnd(source, i + 3, "\"\"\"");
                out.append("\"\"\"");
                blank(source, i + 3, end - 3, out);
                out.append("\"\"\"");
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = literalEnd(source, i + 1, String.valueOf(c));
                out.append(c);
                blank(source, i + 1, end - 1, out);
                out.append(c);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int literalEnd(String source, int from, String delimiter) {
        int i = from;
        while (i < source.length()) {
            if (source.charAt(i) == '\\') {
                i += 2;
            } else if (source.startsWith(delimiter, i)) {
                return i + delimiter.length();
            } else {
                i++;
            }
        }
        throw new IllegalStateException("닫히지 않은 리터럴: " + from);
    }

    private static void blank(String source, int from, int to, StringBuilder out) {
        for (int i = from; i < to; i++) {
            out.append(source.charAt(i) == '\n' ? '\n' : ' ');
        }
    }
}
