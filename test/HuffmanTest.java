import me.stexe.http2.Huffman;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class HuffmanTest {
    @Test
    void decodeSpace() {
        var str = Huffman.decode(new byte[] {0x53});
        assertArrayEquals(" ".getBytes(StandardCharsets.US_ASCII), str);
    }

    @Test
    void decodeHello() {
        var str = Huffman.decode(new byte[] {(byte) 0x9c, (byte) 0xb4, 0x50, 0x7f});
        assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), str);
    }

    @Test
    void decodeHelloWorld() {
        var str = Huffman.decode(new byte[] {(byte) 0x9c, (byte) 0xb4, 0x50, 0x75, 0x3c, 0x1e, (byte) 0xca, 0x24});
        assertArrayEquals("hello world".getBytes(StandardCharsets.US_ASCII), str);
    }

    @Test
    void decodeZ8Bits() {
        var str = Huffman.decode(new byte[] {(byte) 0xfd});
        assertArrayEquals("Z".getBytes(StandardCharsets.US_ASCII), str);
    }

    @Test
    void decodeEmpty() {
        var str = Huffman.decode(new byte[] {});
        assertArrayEquals(new byte[0], str);
    }

    @Test
    void decodeSingleA() {
        var str = Huffman.decode(new byte[] {0x1f});
        assertArrayEquals("a".getBytes(StandardCharsets.US_ASCII), str);
    }

    // Two 8-bit symbols, no padding
    @Test
    void decodeZX() {
        var str = Huffman.decode(new byte[] {(byte) 0xfd, (byte) 0xfc});
        assertArrayEquals("ZX".getBytes(StandardCharsets.US_ASCII), str);
    }

    // Five '0' chars — exercises repeated 5-bit symbols across byte boundaries
    @Test
    void decodeFiveZeros() {
        var str = Huffman.decode(new byte[] {0x00, 0x00, 0x00, 0x7f});
        assertArrayEquals("00000".getBytes(StandardCharsets.US_ASCII), str);
    }

    // Mix of 6, 7, 8-bit symbols and punctuation — exercises multi-level jumps
    @Test
    void decodeHelloWorldExclamation() {
        var str = Huffman.decode(new byte[] {
            (byte) 0xc6, 0x5a, 0x28, 0x3f, (byte) 0xd2,
            (byte) 0x9c, (byte) 0x8f, 0x65, 0x12, 0x7f, 0x1f
        });
        assertArrayEquals("Hello, World!".getBytes(StandardCharsets.US_ASCII), str);
    }

    // Long URL — exercises many jump-table paths
    @Test
    void decodeUrl() {
        var str = Huffman.decode(new byte[] {
            (byte) 0x9d, 0x29, (byte) 0xad, 0x17, 0x18, 0x60, (byte) 0xbe, 0x47,
            0x4d, 0x74, 0x15, 0x72, 0x1e, (byte) 0x96, 0x2b, 0x1a, 0x67, (byte) 0xff,
            0x3b, 0x5a, 0x5b, 0x3d, 0x41, (byte) 0xdc, 0x74, 0x5a, 0x5f
        });
        assertArrayEquals("https://example.com/path?query=value".getBytes(StandardCharsets.US_ASCII), str);
    }

    // 0x00 — 13-bit code, requires one jump
    @Test
    void decode13BitSymbol() {
        var str = Huffman.decode(new byte[] {(byte) 0xff, (byte) 0xc7});
        assertArrayEquals(new byte[] {0x00}, str);
    }

    // 0x09 — 24-bit code, requires two jumps
    @Test
    void decode24BitSymbol() {
        var str = Huffman.decode(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xea});
        assertArrayEquals(new byte[] {0x09}, str);
    }

    // 0x00 (13 bits) followed by 0x09 (24 bits)
    @Test
    void decode13BitFollowedBy24Bit() {
        var str = Huffman.decode(new byte[] {(byte) 0xff, (byte) 0xc7, (byte) 0xff, (byte) 0xff, 0x57});
        assertArrayEquals(new byte[] {0x00, 0x09}, str);
    }

    // '~' (13 bits) + 0x00 (13 bits) + '!' (10 bits) — mix of multi-level codes
    @Test
    void decodeMixedLongCodes() {
        var str = Huffman.decode(new byte[] {(byte) 0xff, (byte) 0xef, (byte) 0xfe, 0x3f, (byte) 0x8f});
        assertArrayEquals(new byte[] {0x7e, 0x00, 0x21}, str);
    }

    // 0x01 (23 bits) + 0x02 (28 bits) + 0x03 (28 bits)
    @Test
    void decodeVeryLongCodes() {
        var str = Huffman.decode(new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xb1, (byte) 0xff, (byte) 0xff,
            (byte) 0xfc, 0x5f, (byte) 0xff, (byte) 0xff, (byte) 0xc7
        });
        assertArrayEquals(new byte[] {0x01, 0x02, 0x03}, str);
    }
}
