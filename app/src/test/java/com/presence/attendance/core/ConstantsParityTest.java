package com.presence.attendance.core;

import static org.junit.Assert.assertEquals;

import com.presence.attendance.testutil.Contracts;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/**
 * Fails when {@link Constants} and {@code contracts/constants.json} disagree in either direction.
 */
public final class ConstantsParityTest {

    @Test
    public void constantsMatchTheContractFile() throws IOException, IllegalAccessException {
        final Map<String, String> contract =
                new TreeMap<>(Contracts.flatObjects(Contracts.read("constants.json")).get(0));

        final Map<String, String> declared = new TreeMap<>();
        for (Field field : Constants.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                declared.put(field.getName(), normalise(field.get(null)));
            }
        }
        contract.replaceAll(
                (name, value) -> isNumber(value) ? normalise(Double.valueOf(value)) : value);

        assertEquals(contract, declared);
    }

    /** Numbers compare by value, so 111320 in JSON equals 111320.0 in Java. */
    private static String normalise(Object value) {
        return value instanceof Number
                ? Double.toString(((Number) value).doubleValue())
                : String.valueOf(value);
    }

    private static boolean isNumber(String value) {
        return value.matches("-?[0-9]+(\\.[0-9]+)?");
    }
}
