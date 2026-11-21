package com.housingplatform.visitor.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;

/** PNG QR codes without the zxing javase module (which drags in AWT helpers we do not need). */
public final class QrCodes {
  private QrCodes() {}

  public static byte[] png(String content, int size) {
    try {
      BitMatrix m =
          new QRCodeWriter()
              .encode(
                  content,
                  BarcodeFormat.QR_CODE,
                  size,
                  size,
                  Map.of(
                      EncodeHintType.ERROR_CORRECTION,
                      ErrorCorrectionLevel.M,
                      EncodeHintType.MARGIN,
                      1,
                      EncodeHintType.CHARACTER_SET,
                      "UTF-8"));
      BufferedImage img =
          new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
      int dark = 0x2e1065; // site violet
      for (int y = 0; y < m.getHeight(); y++) {
        for (int x = 0; x < m.getWidth(); x++) {
          img.setRGB(x, y, m.get(x, y) ? dark : 0xffffff);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("QR generation failed", e);
    }
  }
}
