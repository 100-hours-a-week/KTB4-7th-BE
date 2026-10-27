package com.memme.service.solution.external;

import com.memme.exception.solution.ExternalDataLookupException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

final class KmaWeatherAlertAreaMapper {

    private static final String COUNTRY_AREA_CODE = "L1000000";
    private static final String SEOUL_AREA_CODE = "L1100000";
    private static final Map<String, String> SEOUL_DISTRICT_AREAS = Map.ofEntries(
            Map.entry("강남", "L1100100"), Map.entry("서초", "L1100100"),
            Map.entry("송파", "L1100100"), Map.entry("강동", "L1100100"),
            Map.entry("성동", "L1100200"), Map.entry("광진", "L1100200"),
            Map.entry("동대문", "L1100200"), Map.entry("중랑", "L1100200"),
            Map.entry("성북", "L1100200"), Map.entry("강북", "L1100200"),
            Map.entry("도봉", "L1100200"), Map.entry("노원", "L1100200"),
            Map.entry("강서", "L1100300"), Map.entry("양천", "L1100300"),
            Map.entry("구로", "L1100300"), Map.entry("금천", "L1100300"),
            Map.entry("영등포", "L1100300"), Map.entry("동작", "L1100300"),
            Map.entry("관악", "L1100300"),
            Map.entry("용산", "L1100400"), Map.entry("종로", "L1100400"),
            Map.entry("중", "L1100400"), Map.entry("은평", "L1100400"),
            Map.entry("서대문", "L1100400"), Map.entry("마포", "L1100400")
    );

    private final Map<String, Region> regions;

    KmaWeatherAlertAreaMapper() {
        this.regions = loadRegions();
    }

    Set<String> applicableAreaCodes(String address) {
        if (address == null || address.isBlank()) {
            throw unmappedAddress();
        }

        List<String> rawTokens = List.of(address.split("[^가-힣0-9]+"));
        Set<String> tokens = new HashSet<>();
        for (String token : rawTokens) {
            String normalized = normalizeAdministrativeName(token);
            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
        }

        Region root = findAdministrativeRoot(rawTokens);
        if (root == null) {
            throw unmappedAddress();
        }

        Region location = root.code().equals(SEOUL_AREA_CODE)
                ? resolveSeoulDistrict(root, rawTokens)
                : resolveLocation(root, tokens);
        return ancestorsOf(location);
    }

    boolean isKnownLandArea(String code) {
        return regions.containsKey(code) && code.startsWith("L");
    }

    private Region resolveSeoulDistrict(Region root, List<String> rawTokens) {
        boolean hasDistrictToken = rawTokens.stream().anyMatch(token -> token.endsWith("구"));
        for (String token : rawTokens) {
            if (!token.endsWith("구")) {
                continue;
            }
            String district = normalizeAdministrativeName(token);
            String areaCode = SEOUL_DISTRICT_AREAS.get(district);
            if (areaCode == null) {
                throw unmappedAddress();
            }
            Region region = regions.get(areaCode);
            if (region == null) {
                throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
            }
            return region;
        }
        if (hasDistrictToken) {
            throw unmappedAddress();
        }
        return root;
    }

    private Region resolveLocation(Region root, Set<String> tokens) {
        List<Region> matchingRegions = regions.values().stream()
                .filter(region -> !region.code().equals(root.code()))
                .filter(region -> isDescendantOf(region, root.code()))
                .filter(region -> tokens.contains(normalizeAdministrativeName(region.name())))
                .toList();
        if (matchingRegions.isEmpty()) {
            return root;
        }

        int deepestLevel = matchingRegions.stream().mapToInt(this::depth).max().orElse(0);
        List<Region> mostSpecific = matchingRegions.stream()
                .filter(region -> depth(region) == deepestLevel)
                .toList();
        if (mostSpecific.size() != 1) {
            throw unmappedAddress();
        }
        return mostSpecific.getFirst();
    }

    private Region findAdministrativeRoot(List<String> rawTokens) {
        List<Region> roots = regions.values().stream()
                .filter(region -> COUNTRY_AREA_CODE.equals(region.parentCode()))
                .filter(region -> !region.name().contains("."))
                .toList();
        for (String rawToken : rawTokens) {
            String token = normalizeAdministrativeName(rawToken);
            if (token.isBlank()) {
                continue;
            }
            List<Region> matchingRoots = roots.stream()
                    .filter(region -> token.equals(normalizeAdministrativeName(region.name())))
                    .toList();
            if (matchingRoots.size() == 1) {
                return matchingRoots.getFirst();
            }
            if (matchingRoots.size() > 1) {
                return null;
            }
        }
        return null;
    }

    private Set<String> ancestorsOf(Region region) {
        Set<String> codes = new LinkedHashSet<>();
        Region current = region;
        while (current != null && codes.add(current.code())) {
            current = regions.get(current.parentCode());
        }
        return Set.copyOf(codes);
    }

    private boolean isDescendantOf(Region region, String ancestorCode) {
        Region current = region;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current.code())) {
            if (current.parentCode().equals(ancestorCode)) {
                return true;
            }
            current = regions.get(current.parentCode());
        }
        return false;
    }

    private int depth(Region region) {
        int depth = 0;
        Region current = region;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current.code())) {
            depth++;
            current = regions.get(current.parentCode());
        }
        return depth;
    }

    private Map<String, Region> loadRegions() {
        Map<String, Region> loaded = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("solution/kma-weather-alert-regions.csv").getInputStream(),
                StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("REG_ID,")) {
                    continue;
                }
                String[] columns = line.replace("\uFEFF", "").split(",", 3);
                if (columns.length != 3) {
                    throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
                }
                String code = columns[0].trim();
                String parentCode = columns[1].trim();
                String name = columns[2].trim();
                // The supplied KMA table contains both land (L...) and marine (S...) areas.
                // This service intentionally maps store addresses to land areas only.
                if (code.startsWith("S")) {
                    continue;
                }
                if (!code.startsWith("L") || parentCode.isBlank() || name.isBlank()
                        || loaded.putIfAbsent(code, new Region(code, parentCode, name)) != null) {
                    throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
                }
            }
        } catch (IOException exception) {
            throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
        }
        if (loaded.isEmpty() || !loaded.containsKey(COUNTRY_AREA_CODE)
                || !loaded.containsKey(SEOUL_AREA_CODE)) {
            throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
        }
        return Map.copyOf(loaded);
    }

    private String normalizeAdministrativeName(String name) {
        String normalized = name.replaceAll("[^가-힣0-9]", "");
        for (String suffix : List.of("특별자치도", "특별자치시", "특별시", "광역시", "자치도", "자치시",
                "도", "시", "군", "구", "읍", "면", "동")) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    private ExternalDataLookupException unmappedAddress() {
        return new ExternalDataLookupException("KMA_ALERT_AREA_UNMAPPED");
    }

    private record Region(String code, String parentCode, String name) {
    }
}
