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
    private static final String BUSAN_AREA_CODE = "L1150000";
    private static final String ULSAN_AREA_CODE = "L1160000";
    private static final Map<String, String> BUSAN_DISTRICT_AREAS = Map.ofEntries(
            Map.entry("기장", "L1082500"), Map.entry("해운대", "L1082500"),
            Map.entry("수영", "L1082500"), Map.entry("남", "L1082500"),
            Map.entry("금정", "L1082600"), Map.entry("북", "L1082600"),
            Map.entry("동래", "L1082600"), Map.entry("연제", "L1082600"),
            Map.entry("부산진", "L1082600"), Map.entry("사상", "L1082600"),
            Map.entry("강서", "L1082700"), Map.entry("사하", "L1082700"),
            Map.entry("영도", "L1082700"), Map.entry("서", "L1082700"),
            Map.entry("동", "L1082700"), Map.entry("중", "L1082700")
    );
    private static final Map<String, String> ULSAN_DISTRICT_AREAS = Map.ofEntries(
            Map.entry("북", "L1082800"), Map.entry("동", "L1082800"),
            Map.entry("중", "L1082800"), Map.entry("남", "L1082800"),
            Map.entry("울주", "L1082900")
    );

    private final Map<String, Region> regions;
    private final Set<String> marineAreaCodes;

    KmaWeatherAlertAreaMapper() {
        RegionCatalog catalog = loadRegions();
        this.regions = catalog.landAreas();
        this.marineAreaCodes = catalog.marineAreaCodes();
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

        Region location = switch (root.code()) {
            case SEOUL_AREA_CODE -> resolveDistrict(root, rawTokens, SEOUL_DISTRICT_AREAS);
            case BUSAN_AREA_CODE -> resolveDistrict(root, rawTokens, BUSAN_DISTRICT_AREAS);
            case ULSAN_AREA_CODE -> resolveDistrict(root, rawTokens, ULSAN_DISTRICT_AREAS);
            default -> resolveLocation(root, tokens);
        };
        return ancestorsOf(location);
    }

    boolean isKnownLandArea(String code) {
        return regions.containsKey(code) && code.startsWith("L");
    }

    boolean isKnownMarineArea(String code) {
        return marineAreaCodes.contains(code);
    }

    private Region resolveDistrict(Region root, List<String> rawTokens, Map<String, String> districtAreas) {
        for (String token : rawTokens) {
            if (!token.endsWith("구") && !token.endsWith("군")) {
                continue;
            }
            String district = normalizeAdministrativeName(token);
            String areaCode = districtAreas.get(district);
            if (areaCode == null) {
                throw unmappedAddress();
            }
            Region region = regions.get(areaCode);
            if (region == null) {
                throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
            }
            return region;
        }
        if (rawTokens.stream().anyMatch(token -> token.endsWith("구") || token.endsWith("군"))) {
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
            // Never turn an unrecognized local address into a province-wide alert match.
            throw unmappedAddress();
        }

        int deepestLevel = matchingRegions.stream().mapToInt(this::depth).max().orElse(0);
        List<Region> mostSpecific = matchingRegions.stream()
                .filter(region -> depth(region) == deepestLevel)
                .toList();
        if (mostSpecific.size() != 1) {
            throw unmappedAddress();
        }
        Region location = mostSpecific.getFirst();
        // A KMA area with child zones cannot be resolved from its parent-city name alone.
        // Failing closed is safer than treating a city-wide parent as an exact store area.
        if (regions.values().stream().anyMatch(region -> region.parentCode().equals(location.code()))) {
            throw unmappedAddress();
        }
        return location;
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

    private RegionCatalog loadRegions() {
        Map<String, Region> loaded = new HashMap<>();
        Set<String> marineAreas = new HashSet<>();
        Set<String> allCodes = new HashSet<>();
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
                if (parentCode.isBlank() || name.isBlank() || !allCodes.add(code)) {
                    throw new ExternalDataLookupException("KMA_ALERT_AREAS_INVALID");
                }
                // Keep marine codes for validating API rows, but never map stores to sea areas.
                if (code.startsWith("S")) {
                    marineAreas.add(code);
                    continue;
                }
                if (!code.startsWith("L")
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
        return new RegionCatalog(Map.copyOf(loaded), Set.copyOf(marineAreas));
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

    private record RegionCatalog(Map<String, Region> landAreas, Set<String> marineAreaCodes) {
    }
}
