package me.stexe.http2;

import java.io.ByteArrayOutputStream;

sealed interface Entry permits Symbol, Jump {}

record Symbol(
   int ch,
   int numBits
) implements Entry {}

record Jump(Entry[] table) implements Entry {}

public class Huffman {
    private static final int[] symbols = new int[] {
        ' ', 0b010100, 6,

        'e', 0b00101, 5,
        'h', 0b100111, 6,
        'l', 0b101000, 6,
        'o', 0b00111, 5,
    };


    private static final Entry[] table = new Entry[256];

    static void init() {
        for (int i = 0; i < symbols.length; i += 3) {
            int ch = symbols[i];
            int pattern = symbols[i + 1];
            int numBits = symbols[i + 2];

            if (numBits < 8) {
                int sh = 8 - numBits;

                for (int k = 0; k < (1 << sh); k++) {
                    int x = ((pattern << sh) & 0xff) | k;
                    table[x] = new Symbol(ch, numBits);
                }
            } else {
                // TODO: Implement
            }
        }
    }

    static {
        init();
    }

    public static byte[] decode(byte[] bytes) {
        var out = new ByteArrayOutputStream(bytes.length);
        var stream = new BitStream(bytes);

        while (!stream.eos()) {
            switch (table[stream.nextByte() & 0xff]) {
                case Symbol sym -> {
                    stream.consume(sym.numBits());
                    out.write(sym.ch());
                }
                case Jump jmp -> {}
            }
        }

        return out.toByteArray();
    }

    static class BitStream {
        private final byte[] array;
        private int offset = 0;
        private int index = 0;

        public BitStream(byte[] arr) {
            array = arr;
        }

        void consume(int numBits) {
            if (offset + numBits >= 8) {
                index++;
            }

            offset = (offset + numBits) % 8;
        }

        byte nextByte() {
            int next = (index + 1 < array.length) ? (array[index + 1] & 0xff) : 0xff;
            return (byte) ((((array[index] & 0xff) << offset) & 0xff) | (next >> (8 - offset)));
        }

        boolean eos() {
            return index >= array.length || (index + 1 == array.length && offset > 0);
        }
    }
}
