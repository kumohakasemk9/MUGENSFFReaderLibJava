package kumotechmadlab.sffreader;

/**
Common functions used in main program
*/
public class C {
	//Convert data[]'s bytes (from index offs to len times) to integer (little endian, unsigned)
	public static long b2ui(byte data[], int offs, int len) {
		long r = 0;
		for(int i = 0; i < len; i++) {
			r += Byte.toUnsignedInt(data[i + offs]) << (i * 8);
		}
		return r;
	}
	
	//Convert data[]'s bytes (from index offs to len times) to integer (big endian, unsigned)
	public static long b2uibe(byte[] data, int offs, int len) {
		long r = 0;
		for(int i = 0; i < len; i++) {
			r += Byte.toUnsignedInt(data[i + offs]) << ( (len - i - 1) * 8);
		}
		return r;
	}
	
	//Signed 16bit version of b2ui
	public static int b2i16(byte data[], int offs) {
		int r = (int)b2ui(data, offs, 2);
		if(r > 32767) { return 65536 - r; } //Two's complement
		return r;
	}
	
	//Check data (from index offs) bytes are same as data2, return false if different.
	public static boolean compareBytes(byte data[], int offs, byte data2[]) {
		for(int i = 0; i < data2.length; i++) {
			if(data[i + offs] != data2[i]) { return false; }
		}
		return true;
	}
	
	//Returns true if min <= data <= max
	public static boolean in_range(long data, long min, long max) {
		if(min <= data && data <= max) {
			return true;
		}
		return false;
	}
	
	//Convert uint32 to byte[4] and rewrite byte[] t with it from offs
	public static void ui32tob(long d, byte[] t, int offs) {
		byte r[] = new byte[4];
		t[offs+3] = (byte) (d & 0xff);
		t[offs+2] = (byte) ((d >> 8) & 0xff);
		t[offs+1] = (byte) ((d >> 16) & 0xff);
		t[offs] = (byte) ((d >> 24) & 0xff);
	}
	
	//Debug purpose: hexdump -C but without ascii
	public static void hd(byte[] data) {
		System.out.println("offs || +0 +1 +2 +3 +4 +5 +6 +7 +8 +9 +a +b +c +d +e +f");
		for(int i = 0; i < data.length; i++) {
			if(i % 16 == 0) {
				System.out.printf("%04x || ",i);
			}
			System.out.printf("%02x ", data[i]);
			if(i % 16 == 15) {
				System.out.println();
			}
		}
		System.out.println();
	}
}
