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
}
