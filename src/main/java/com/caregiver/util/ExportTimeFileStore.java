package com.caregiver.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ExportTimeFileStore {

    private static final String FILE_PATH = "export-times.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<Long, String> readAll() {
        try {
            File file = new File(FILE_PATH);

            if (!file.exists()) {
                return new HashMap<>();
            }

            return mapper.readValue(file, new TypeReference<Map<Long, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to read export time file", e);
        }
    }

    public static LocalDateTime get(Long patientId) {
        Map<Long, String> map = readAll();

        if (!map.containsKey(patientId)) {
            return null;
        }

        return LocalDateTime.parse(map.get(patientId));
    }

    public static void save(Long patientId, LocalDateTime exportTime) {
        try {
            Map<Long, String> map = readAll();

            map.put(patientId, exportTime.toString());

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(FILE_PATH), map);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save export time file", e);
        }
    }
}