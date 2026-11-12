package com.presence.attendance.course.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.core.Constants;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public final class JoinCodesTest {

    private static final int SAMPLES = 2000;

    @Test
    public void generatedCodesAreValidAndUseOnlyTheContractAlphabet() {
        final Random random = new SecureRandom();
        for (int i = 0; i < SAMPLES; i++) {
            final String code = JoinCodes.generate(random);
            assertEquals(Constants.JOIN_CODE_LENGTH, code.length());
            assertTrue(code, JoinCodes.isValid(code));
        }
    }

    @Test
    public void generatedCodesUseTheWholeAlphabetAndRarelyRepeat() {
        final Random random = new Random(7);
        final Set<String> codes = new HashSet<>();
        final Set<Character> letters = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            final String code = JoinCodes.generate(random);
            codes.add(code);
            for (char c : code.toCharArray()) {
                letters.add(c);
            }
        }
        assertEquals(Constants.JOIN_CODE_ALPHABET.length(), letters.size());
        // 2000 draws from 32^6 codes: more than a couple of repeats means a broken generator.
        assertTrue(codes.size() >= SAMPLES - 2);
    }

    @Test
    public void normaliseTrimsAndUpperCases() {
        assertEquals("ABC234", JoinCodes.normalise("  abc234 \n"));
    }

    @Test
    public void validationIsStrict() {
        assertTrue(JoinCodes.isValid("ABC234"));
        assertFalse("too short", JoinCodes.isValid("ABC23"));
        assertFalse("too long", JoinCodes.isValid("ABC2345"));
        assertFalse("empty", JoinCodes.isValid(""));
        assertFalse("lower case is not repaired here", JoinCodes.isValid("abc234"));
        assertFalse("letter O is excluded", JoinCodes.isValid("ABCDEO"));
        assertFalse("digit 0 is excluded", JoinCodes.isValid("ABCDE0"));
        assertFalse("letter I is excluded", JoinCodes.isValid("ABCDEI"));
        assertFalse("digit 1 is excluded", JoinCodes.isValid("ABCDE1"));
        assertFalse("inner space", JoinCodes.isValid("ABC 34"));
        assertFalse("punctuation", JoinCodes.isValid("ABC-34"));
    }
}
