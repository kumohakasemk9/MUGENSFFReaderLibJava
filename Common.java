package kumotechmadlab.sffreader;

public class Common {
	//Convert data[]'s bytes (from index offs to len times) to integer (little endian, unsigned)
	public static long b2ui(byte data[], int offs, int len) {
		long r = 0;
		for(int i = 0; i < len; i++) {
			r += Byte.toUnsignedInt(data[i + offs]) << (i * 8);
		}
		return r;
	}
	
	//b2ui but big endian version, max len limit is 4
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
}
