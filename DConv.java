package kumotechmadlab.sffreader;

import kumotechmadlab.sffreader.*;
import javax.imageio.*;
import java.awt.image.*;
import java.io.*;
import java.util.zip.*;

//Data converter
public class DConv {
	
	//Convert SFF's internal PNG data[] to BufferedImage (first 4 octets will be skipped)
	public static BufferedImage fromPNG(byte data[]) throws IOException{
		ByteArrayInputStream bi = new ByteArrayInputStream(data, 4, data.length - 4);
		return ImageIO.read(bi);
	}
	
	//SFFConvert2PNG but you can specify external palette for indexed clolur PNG
	//Palette format: ARGB8888 int[]
	public static BufferedImage fromPNG(byte data[], int extpal[]) throws Exception {
		//Make new PLTE chunk data
		final byte sign[] = {'P', 'L', 'T', 'E'};
		byte plte[] = new byte[extpal.length * 3 + 12]; //size=palettesize*3octets+len+crc+type
		C.ui32tob(plte.length - 12, plte, 0); //Copy data size
		System.arraycopy(sign, 0, plte, 4, 4); //Copy PLTE chunk signature
		//convert ARGB8888 int[] to RGB888 byte[]
		for(int i = 0; i < extpal.length; i++) {
			int d = extpal[i];
			plte[i * 3 + 8] = (byte)((d >> 16) & 0xff); //R
			plte[i * 3 + 9] = (byte)((d >> 8) & 0xff); //G
			plte[i * 3 + 10] = (byte)(d & 0xff); //B
		}
		//Calculate CRC32 of data and add to tail
		final CRC32 crc = new CRC32();
		crc.update(plte, 8, plte.length - 12); //Calculate for only palette data
		C.ui32tob(crc.getValue(), plte, plte.length - 4);
		//Find PLTE chunk and get start pos
		int p = 12; //first 4 octets are filesize, next 8 octets are magic number
		int start = -1;
		int end = 0;
		while(p < data.length) {
			int s = (int)C.b2ui(data, p, 4); //read len
			//read chunk type and compare
			if(C.compareBytes(data, p + 4, sign) ) {
				start = p; //offset of PLTE chunk
				end = p + s + 12; //end offset of PLTE, +12 is for chunk type, len and crc
				break;
			}
			p += s + 12;
		}
		//Detect error case
		if(start < 12) {
			throw new Exception();
		}
		//Replace PLTE chunk
		byte newdata[] = new byte[start + (data.length - end) + plte.length];
		System.arraycopy(data, 0, newdata, 0, start); //Copy data before PLTE
		System.arraycopy(plte, 0, newdata, start, plte.length); //Copy new PLTE chunk
		//Copy data after PLTE
		System.arraycopy(data, end, newdata, start + plte.length, data.length - end);
		return fromPNG(newdata);
	}
}
