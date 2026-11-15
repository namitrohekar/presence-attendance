package com.presence.attendance.session.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

public final class QrMatrixTest {

    private static final String PAYLOAD =
            QrPayload.build("kYp3Qv9sT2mLx7Zb1cDe", "AbCdEfGhIjKlMnOpQrSt_-");

    @Test
    public void thePayloadSurvivesEncodingAndDecoding() throws Exception {
        final BitMatrix matrix = QrMatrix.encode(PAYLOAD);
        final int size = matrix.getWidth();
        final int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        final Result result =
                new QRCodeReader()
                        .decode(
                                new BinaryBitmap(
                                        new HybridBinarizer(
                                                new RGBLuminanceSource(size, size, pixels))),
                                hints);
        assertEquals(PAYLOAD, result.getText());
        assertEquals(
                "error correction level M",
                ErrorCorrectionLevel.M.toString(),
                String.valueOf(
                        result.getResultMetadata()
                                .get(com.google.zxing.ResultMetadataType.ERROR_CORRECTION_LEVEL)));
    }

    @Test
    public void theQuietZoneIsFourWhiteModulesOnEverySide() {
        final BitMatrix matrix = QrMatrix.encode(PAYLOAD);
        final int size = matrix.getWidth();
        assertEquals("square", size, matrix.getHeight());
        for (int i = 0; i < size; i++) {
            for (int edge = 0; edge < QrMatrix.QUIET_ZONE; edge++) {
                assertFalse(matrix.get(i, edge));
                assertFalse(matrix.get(i, size - 1 - edge));
                assertFalse(matrix.get(edge, i));
                assertFalse(matrix.get(size - 1 - edge, i));
            }
        }
        assertTrue(
                "the code starts right after the quiet zone",
                matrix.get(QrMatrix.QUIET_ZONE, QrMatrix.QUIET_ZONE));
    }
}
