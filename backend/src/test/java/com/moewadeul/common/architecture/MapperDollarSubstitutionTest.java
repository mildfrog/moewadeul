package com.moewadeul.common.architecture;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class MapperDollarSubstitutionTest {

    private static final Path MAIN_MAPPER_ROOT = Path.of("src/main/resources/mapper");
    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/fixtures/mapper-dollar-substitution");

    private static final Pattern DOLLAR = Pattern.compile("\\$\\{([^}]*)}");

    // property 없이 실행 시점 값으로 채우는 ${}다. 여기 적힌 조각과 이름만 규칙 2에서 뺀다.
    private static final Map<String, Exemption> EXEMPTIONS = Map.ofEntries(entry(
            "com.moewadeul.trust.infra.report.AdminMemberReportMapper.typePriority",
            new Exemption(
                    Set.of("type.name()", "type.priority()"),
                    "foreach가 ReportType@values()의 enum 상수만 돌아 사용자 입력이 들어갈 길이 없다")));

    private record Exemption(Set<String> names, String reason) {}

    private record MapperFile(String file, String namespace, Element root) {}

    private record Include(String where, String namespace, Element include, Element enclosingSql) {}

    private record Scan(int dollarCount, List<String> violations) {}

    @Test
    @DisplayName("[NFR-07] 매퍼의 ${}는 공용 <sql> 조각 안에서 include의 글자 property로만 쓴다")
    void mapperDollarSubstitutionIsConfinedToSqlFragments() throws Exception {
        Scan scan = scan(MAIN_MAPPER_ROOT);

        assertThat(scan.dollarCount()).as("검사한 ${} 개수").isPositive();
        assertThat(scan.violations()).as("매퍼 ${} 위반").isEmpty();
    }

    @Test
    @DisplayName("[NFR-07] select·insert·update·delete 안에 ${}를 쓰면 실패한다")
    void dollarInStatementFails() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("statement")).violations())
                .containsExactlyInAnyOrder(
                        "probe/ProbeMapper.xml#search: <sql> 밖에서 ${condition.keyword}를 쓴다",
                        "probe/ProbeMapper.xml#insertName: <sql> 밖에서 ${name}를 쓴다",
                        "probe/ProbeMapper.xml#updateName: <sql> 밖에서 ${column}를 쓴다",
                        "probe/ProbeMapper.xml#deleteFrom: <sql> 밖에서 ${table}를 쓴다");
    }

    @Test
    @DisplayName("[NFR-07] include가 property를 빠뜨리거나 #{}·${} 값을 넘기면 실패한다")
    void includeMustPassLiteralProperty() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("include")).violations())
                .containsExactlyInAnyOrder(
                        "probe/ProbeMapper.xml#missing: com.moewadeul.common.probe.ProbeSql.selectAll의 ${table} 값을 넘기지 않았다",
                        "probe/ProbeMapper.xml#parameter: com.moewadeul.common.probe.ProbeSql.selectAll의 ${table}에"
                                + " 글자가 아닌 값 #{table}을 넘긴다",
                        "probe/ProbeMapper.xml#dollar: com.moewadeul.common.probe.ProbeSql.selectAll의 ${table}에"
                                + " 글자가 아닌 값 ${table}을 넘긴다",
                        "probe/ProbeMapper.xml#dollar: <sql> 밖에서 ${table}를 쓴다");
    }

    @Test
    @DisplayName("[NFR-07] 동적 refid는 부르는 쪽이 넘긴 조각까지 풀어 property를 확인한다")
    void dynamicRefidIsResolvedThroughCaller() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("dynamic-refid")).violations())
                .containsExactly("common/probe/ProbeSql.xml#reports: com.moewadeul.common.probe.ProbeSql.byColumn의"
                        + " ${column} 값을 넘기지 않았다");
    }

    @Test
    @DisplayName("[NFR-07] 주석 안의 ${}는 무시하고, 문장 안 속성 값의 ${}는 실패한다")
    void commentIsIgnoredButAttributeIsChecked() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("comment-and-attribute")).violations())
                .containsExactly("probe/ProbeMapper.xml#dynamicInclude: <sql> 밖에서 ${scope}를 쓴다");
    }

    private static Scan scan(Path root) throws Exception {
        List<MapperFile> mappers = load(root);
        Map<String, Set<String>> fragments = new HashMap<>();
        List<Include> includes = new ArrayList<>();
        Set<String> violations = new LinkedHashSet<>();
        int[] dollarCount = {0};

        for (MapperFile mapper : mappers) {
            for (Element top : childElements(mapper.root())) {
                String where = mapper.file() + "#" + top.getAttribute("id");
                boolean isSql = top.getTagName().equals("sql");
                Set<String> names = new LinkedHashSet<>();
                walk(top, top, mapper.namespace(), where, includes, dollar -> {
                    dollarCount[0]++;
                    if (isSql) {
                        names.add(dollar);
                    } else {
                        violations.add(where + ": <sql> 밖에서 ${" + dollar + "}를 쓴다");
                    }
                });
                if (isSql) {
                    String key = mapper.namespace() + "." + top.getAttribute("id");
                    Exemption exemption = EXEMPTIONS.get(key);
                    if (exemption != null) {
                        names.removeAll(exemption.names());
                    }
                    fragments.put(key, names);
                }
            }
        }

        for (Include include : includes) {
            for (String target : targets(include, includes)) {
                Set<String> names = fragments.get(target);
                if (names == null) {
                    violations.add(include.where() + ": include할 조각 " + target + "을 찾지 못했다");
                    continue;
                }
                Map<String, String> properties = properties(include.include());
                for (String name : names) {
                    String value = properties.get(name);
                    if (value == null) {
                        violations.add(include.where() + ": " + target + "의 ${" + name + "} 값을 넘기지 않았다");
                    } else if (value.contains("#{") || value.contains("${")) {
                        violations.add(
                                include.where() + ": " + target + "의 ${" + name + "}에 글자가 아닌 값 " + value + "을 넘긴다");
                    }
                }
            }
        }
        return new Scan(dollarCount[0], List.copyOf(violations));
    }

    // refid가 ${이름}이면 감싼 <sql>을 부르는 include가 넘긴 그 이름의 값으로 대상을 정한다
    private static List<String> targets(Include include, List<Include> includes) {
        String refid = include.include().getAttribute("refid");
        Matcher dynamic = DOLLAR.matcher(refid);
        if (!dynamic.matches()) {
            return List.of(qualify(refid, include.namespace()));
        }
        if (include.enclosingSql() == null) {
            return List.of();
        }
        String name = dynamic.group(1);
        String enclosingKey = include.namespace() + "." + include.enclosingSql().getAttribute("id");
        List<String> targets = new ArrayList<>();
        for (Include caller : includes) {
            if (qualify(caller.include().getAttribute("refid"), caller.namespace())
                    .equals(enclosingKey)) {
                String value = properties(caller.include()).get(name);
                if (value != null && !value.contains("{")) {
                    targets.add(qualify(value, caller.namespace()));
                }
            }
        }
        return targets;
    }

    private interface DollarSink {
        void accept(String name);
    }

    private static void walk(
            Element element, Element top, String namespace, String where, List<Include> includes, DollarSink sink) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            collect(((Attr) attributes.item(i)).getValue(), sink);
        }
        if (element.getTagName().equals("include")) {
            includes.add(new Include(where, namespace, element, top.getTagName().equals("sql") ? top : null));
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                collect(child.getNodeValue(), sink);
            } else if (child instanceof Element childElement) {
                walk(childElement, top, namespace, where, includes, sink);
            }
        }
    }

    private static void collect(String text, DollarSink sink) {
        Matcher matcher = DOLLAR.matcher(text);
        while (matcher.find()) {
            sink.accept(matcher.group(1));
        }
    }

    private static Map<String, String> properties(Element include) {
        Map<String, String> properties = new HashMap<>();
        for (Element property : childElements(include)) {
            if (property.getTagName().equals("property")) {
                properties.put(property.getAttribute("name"), property.getAttribute("value"));
            }
        }
        return properties;
    }

    private static String qualify(String refid, String namespace) {
        return refid.contains(".") ? refid : namespace + "." + refid;
    }

    private static List<MapperFile> load(Path root) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setCoalescing(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(path -> path.toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
        List<MapperFile> mappers = new ArrayList<>();
        for (Path path : paths) {
            Element mapper = builder.parse(path.toFile()).getDocumentElement();
            mappers.add(new MapperFile(root.relativize(path).toString(), mapper.getAttribute("namespace"), mapper));
        }
        return mappers;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }
}
