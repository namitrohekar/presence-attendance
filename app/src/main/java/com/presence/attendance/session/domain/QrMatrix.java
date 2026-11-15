package com.presence.attendance.session.domain;

import androidx.annotation.NonNull;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Encodes text as a QR module matrix: error correction M and a quiet zone of four modules, one
 * matrix cell per module. Plain square modules, nothing decorative, so any scanner reads it.
 */
public final class QrMatrix {

    /** Modules of white border on every side, the minimum the QR standard requires. */
    public static final int QUIET_ZONE = 4;

    private QrMatrix() {}

    /**
     * @return the matrix including its quiet zone; a set bit is a dark module
     * @throws IllegalArgumentException if the text cannot be encoded
     */
    @NonNull
    public static BitMatrix encode(@NonNull String text) {
        final Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE);
        try {
            // Size 0 asks for the natural size: exactly one cell per module.
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException tooLong) {
            throw new IllegalArgumentException("Text cannot be encoded as a QR code", tooLong);
        }
    }
}
