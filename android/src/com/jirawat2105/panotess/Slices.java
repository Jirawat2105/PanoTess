package com.jirawat2105.panotess;

/** Pure integer geometry, shared by preview and full-resolution export. */
public final class Slices {
    public static int[][] boxes(int width, int height, int count, int a, int b) {
        if (count < 2 || count > 4 || width < count || height < 1) throw new IllegalArgumentException("E_IMAGE_SMALL: Image is too small.");
        int[][] boxes = new int[count][4];
        int tileW = 0, tileH = height, x = 0, y = 0;
        if (a != 0) {
            int unit = Math.min(width / (a * count), height / b);
            if (unit == 0) throw new IllegalArgumentException("E_IMAGE_SMALL: Image is too small for this ratio.");
            tileW = unit * a; tileH = unit * b;
            x = (width - tileW * count) / 2; y = (height - tileH) / 2;
        }
        for (int i = 0; i < count; i++) {
            boxes[i] = new int[]{a == 0 ? (int)((long)i * width / count) : x + i * tileW,
                y, a == 0 ? (int)((long)(i + 1) * width / count) : x + (i + 1) * tileW, y + tileH};
        }
        return boxes;
    }
}
