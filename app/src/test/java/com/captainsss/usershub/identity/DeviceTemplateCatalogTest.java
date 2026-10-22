package com.captainsss.usershub.identity;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class DeviceTemplateCatalogTest {
    private static List<DeviceTemplateCatalog.Template> catalog() throws Exception {
        Path path = Paths.get("app/src/main/assets/device_templates.json");
        if (!Files.exists(path)) path = Paths.get("src/main/assets/device_templates.json");
        return DeviceTemplateCatalog.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    @Test public void bundledCatalogIsValidAndSourceAttributed() throws Exception {
        List<DeviceTemplateCatalog.Template> templates = catalog();
        assertEquals(44, templates.size());
        Set<String> ids = new HashSet<>(); int official = 0, community = 0, review = 0, random = 0;
        for (DeviceTemplateCatalog.Template template : templates) {
            assertTrue(ids.add(template.id)); assertTrue(template.sourceUrl.startsWith("https://"));
            if ("official".equals(template.verification)) official++;
            if ("community".equals(template.verification)) community++;
            if ("review".equals(template.verification)) review++;
            if (template.randomEligible) {
                random++; assertNotNull(template.values.get("hardware"));
                assertTrue(Integer.parseInt(template.androidRelease().split("\\.")[0]) >= 10);
            }
        }
        assertEquals(1, official); assertEquals(43, community); assertEquals(0, review); assertEquals(25, random);
    }

    @Test public void randomPoolNeverReturnsPartialOrReviewTemplate() throws Exception {
        List<DeviceTemplateCatalog.Template> templates = catalog();
        for (int i = 0; i < 100; i++) {
            DeviceTemplateCatalog.Template template = DeviceTemplateCatalog.randomComplete(templates, 12);
            assertTrue(template.randomEligible); assertEquals("community", template.verification);
            assertNotNull(template.values.get("hardware")); assertEquals(12, template.androidMajor());
        }
    }

    @Test public void fingerprintCompanionsAreDerivedAndValidated() throws Exception {
        DeviceTemplateCatalog.Template pixel = catalog().get(0);
        assertEquals("SD1A.210817.015.A4", pixel.values.get("build_id"));
        assertEquals("7697517", pixel.values.get("build_incremental"));
        assertEquals("user", pixel.values.get("build_type"));
        assertEquals("release-keys", pixel.values.get("build_tags"));
        for (String key : new String[]{"build_id", "build_incremental", "build_type", "build_tags"})
            IdentityFields.validate(key, pixel.values.get(key));
    }

    @Test public void randomTemplateMustMatchHostAndroidRelease() throws Exception {
        List<DeviceTemplateCatalog.Template> templates = catalog();
        for (int release : new int[]{10, 12, 13, 14, 15})
            assertEquals(release, DeviceTemplateCatalog.randomComplete(templates, release).androidMajor());
        assertThrows(IllegalStateException.class, () -> DeviceTemplateCatalog.randomComplete(templates, 99));
    }

    @Test public void rejectsFingerprintWithDifferentDeviceCode() {
        String invalid = "{\"version\":1,\"templates\":[{\"id\":\"bad\",\"name\":\"Bad\",\"verification\":\"community\",\"randomEligible\":false,\"sourceName\":\"test\",\"sourceUrl\":\"https://example.com\",\"note\":\"test\",\"values\":{\"brand\":\"google\",\"manufacturer\":\"Google\",\"model\":\"Pixel\",\"device\":\"oriole\",\"product\":\"oriole\",\"fingerprint\":\"google/oriole/raven:12/X/Y:user/release-keys\"}}]}";
        assertThrows(Exception.class, () -> DeviceTemplateCatalog.parse(invalid));
    }

    @Test public void rejectsFingerprintWithDifferentProductCode() {
        String invalid = "{\"version\":1,\"templates\":[{\"id\":\"bad\",\"name\":\"Bad\",\"verification\":\"community\",\"randomEligible\":false,\"sourceName\":\"test\",\"sourceUrl\":\"https://example.com\",\"note\":\"test\",\"values\":{\"brand\":\"google\",\"manufacturer\":\"Google\",\"model\":\"Pixel\",\"device\":\"oriole\",\"product\":\"raven\",\"fingerprint\":\"google/oriole/oriole:12/X/Y:user/release-keys\"}}]}";
        assertThrows(Exception.class, () -> DeviceTemplateCatalog.parse(invalid));
    }

    @Test public void rejectsOldAndroidFromRandomPool() {
        String invalid = "{\"version\":1,\"templates\":[{\"id\":\"old\",\"name\":\"Old\",\"verification\":\"community\",\"randomEligible\":true,\"sourceName\":\"test\",\"sourceUrl\":\"https://example.com\",\"note\":\"test\",\"values\":{\"brand\":\"google\",\"manufacturer\":\"Google\",\"model\":\"Pixel\",\"device\":\"sargo\",\"product\":\"sargo\",\"hardware\":\"sargo\",\"fingerprint\":\"google/sargo/sargo:9/X/Y:user/release-keys\"}}]}";
        assertThrows(Exception.class, () -> DeviceTemplateCatalog.parse(invalid));
    }
}
