package com.presence.attendance.session.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.presence.attendance.core.Constants;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class TokensTest {

    private static final int SAMPLES = 5000;

    @Test
    public void generatedTokensHaveTheContractLengthAndCharset() {
        final SecureRandom random = new SecureRandom();
        for (int i = 0; i < SAMPLES; i++) {
            final String token = Tokens.generate(random);
            assertEquals(Constants.TOKEN_LENGTH, token.length());
            assertTrue(token, token.matches("^[A-Za-z0-9_-]{22}$"));
            assertTrue(Tokens.isValid(token));
        }
    }

    @Test
    public void generatedTokensDoNotRepeat() {
        final SecureRandom random = new SecureRandom();
        final Set<String> tokens = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            tokens.add(Tokens.generate(random));
        }
        assertEquals(SAMPLES, tokens.size());
    }

    @Test
    public void validationIsStrict() {
        assertTrue(Tokens.isValid("AbCdEfGhIjKlMnOpQrSt_-"));
        assertFalse("21 characters", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt_"));
        assertFalse("23 characters", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt_-x"));
        assertFalse("standard base64 plus", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt+-"));
        assertFalse("standard base64 slash", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt/-"));
        assertFalse("padding", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt_="));
        assertFalse("a space", Tokens.isValid("AbCdEfGhIjKlMnOpQrSt_ "));
        assertFalse("empty", Tokens.isValid(""));
    }
}
