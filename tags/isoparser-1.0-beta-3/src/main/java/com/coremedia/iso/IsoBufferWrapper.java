/*  
 * Copyright 2008 CoreMedia AG, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an AS IS BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */

package com.coremedia.iso;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * A <code>FilterInputStream</code> enriched with helper methods to ease writing of
 * Iso specific numbers and strings.
 */
public class IsoBufferWrapper {
    ByteBuffer[] parents;
    int activeParent = 0;
  public int readBitsRemaining;
  private byte readBitsBuffer;

  public IsoBufferWrapper(ByteBuffer parent) {
        this.parents = new ByteBuffer[]{parent};
    }

    public IsoBufferWrapper(ByteBuffer[] parents) {
        this.parents = parents;
    }

    public IsoBufferWrapper(List<ByteBuffer> parents) {
        this.parents = parents.toArray(new ByteBuffer[parents.size()]);
    }

    public IsoBufferWrapper(File file) throws IOException {
        long filelength = file.length();
        int sliceSize = 1024 * 1024 * 128;

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
        long i = 0;
        while (i < filelength) {
            if ((filelength - i) > sliceSize) {
                ByteBuffer bb;
                try {
                    bb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, i, sliceSize);//.slice();
                } catch (IOException e1) {

                    try {
                        bb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, i, sliceSize);//.slice();
                    } catch (IOException e2) {

                        try {
                            bb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, i, sliceSize);//.slice();
                        } catch (IOException e3) {
                            bb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, i, sliceSize);//.slice();
                        }
                    }
                }
                buffers.add(bb);
                i += sliceSize;
            } else {
                buffers.add(raf.getChannel().map(FileChannel.MapMode.READ_ONLY, i, filelength - i).slice());
                i += filelength - i;
            }
        }
        parents = buffers.toArray(new ByteBuffer[buffers.size()]);
        raf.close();
    }

    public long position() {
        if (activeParent >= 0) {
            long pos = 0;
            for (int i = 0; i < activeParent; i++) {
                pos += parents[i].limit();
            }
            pos += parents[activeParent].position();
            return pos;
        } else {
            return size();
        }
    }

    public void position(long position) {
        if (position == size()) {
            activeParent = -1;
        } else {
            int current = 0;
            while (position >= parents[current].limit()) {
                position -= parents[current++].limit();
            }
            parents[current].position((int) position);
            activeParent = current;
        }
    }

    public long size() {
        long size = 0;
        for (ByteBuffer parent : parents) {
            size += parent.limit();
        }
        return size;
    }

    public long readUInt64() {
        long result = 0;
        // thanks to Erik Nicolas for finding a bug! Cast to long is definitivly needed
        result += readUInt32() << 32;
        if (result < 0) {
            throw new RuntimeException("I don't know how to deal with UInt64! long is not sufficient and I don't want to use BigInt");
        }
        result += readUInt32();

        return result;
    }

    public long readUInt32() {
        long result = 0;
        result += ((long) readUInt16()) << 16;
        result += readUInt16();
        return result;
    }

    public int readUInt24() {
        int result = 0;
        result += readUInt16() << 8;
        result += readUInt8();
        return result;
    }

    public int readUInt16() {
        int result = 0;
        result += readUInt8() << 8;
        result += readUInt8();
        return result;
    }

    public int readUInt8() {
        byte b = read();
        return b < 0 ? b + 256 : b;
    }

    public byte[] read(int byteCount) {
        byte[] result = new byte[byteCount];
        this.read(result);
        return result;

    }

    public long remaining() {
        if (activeParent == -1) {
            return 0;
        } else {
            long remaining = 0;
            for (int i = activeParent; i < parents.length; i++) {
                remaining += parents[i].remaining();

            }
            return remaining;
        }
    }

    public byte read() {
        if (parents[activeParent].remaining() == 0) {
          if (parents.length > activeParent + 1) {
            activeParent++;
            parents[activeParent].rewind();
            return read();
          } else {
            //todo: consider a parents' rewind on init?
            try {
              throw new RuntimeException("No more bytes to read remaining! Make sure position is set correctly after instantiation.");
            } catch (RuntimeException e) {
              System.out.println("read beyond buffers!");
              e.printStackTrace();
              return 0;
            }
          }
        }
        return parents[activeParent].get();
    }

    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) {
        if (parents[activeParent].remaining() >= len) {
            parents[activeParent].get(b, off, len);
            return len;
        } else {
            int curRemaining = parents[activeParent].remaining();
            parents[activeParent].get(b, off, curRemaining);
            activeParent++;
            parents[activeParent].rewind();
            return curRemaining + read(b, off + curRemaining, len - curRemaining);
        }

    }

    public double readFixedPoint1616() {
        byte[] bytes = read(4);
        int result = 0;
        result |= ((bytes[0] << 24) & 0xFF000000);
        result |= ((bytes[1] << 16) & 0xFF0000);
        result |= ((bytes[2] << 8) & 0xFF00);
        result |= ((bytes[3]) & 0xFF);
        return ((double) result) / 65536;

    }

    public float readFixedPoint88() {
        byte[] bytes = read(2);
        short result = 0;
        result |= ((bytes[0] << 8) & 0xFF00);
        result |= ((bytes[1]) & 0xFF);
        return ((float) result) / 256;
    }

    public String readIso639() {
        int bits = readUInt16();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int c = (bits >> (2 - i) * 5) & 0x1f;
            result.append((char) (c + 0x60));
        }
        return result.toString();
    }

    /**
     * Reads a zero terminated string.
     *
     * @return the string read
     * @throws Error in case of an error in the underlying stream
     */
    public String readString() {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read;
        while ((read = read()) != 0) {
            out.write(read);
        }
        try {
            return out.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error("JVM doesn't support UTF-8");
        }

    }

    public String readString(int length) {
        byte[] buffer = new byte[length];
        this.read(buffer);
        try {
            return new String(buffer, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error("JVM doesn't support UTF-8");
        }
    }

    public long skip(long n) {

        this.position(this.position() + n);
        return n;
    }

    public ByteBuffer[] getSegment(long startPos, long length) {
        ArrayList<ByteBuffer> segments = new ArrayList<ByteBuffer>();
        position(startPos);
        while (length > 0) {
            ByteBuffer currentSlice = parents[activeParent].slice();
            if (currentSlice.remaining() >= length) {
                currentSlice.limit((int) length); // thats ok we tested in the line before
                length -= length;
            } else {
                // ok use up current bytebuffer and jump to next
                length -= currentSlice.remaining();
                parents[++activeParent].rewind();
            }
            segments.add(currentSlice);
        }
        return segments.toArray(new ByteBuffer[segments.size()]);
    }


  public long readUInt32BE() {
    long result = 0;
    result += readUInt16BE();
    result += ((long) readUInt16BE()) << 16;
    return result;
  }

  public int readUInt16BE() {
    int result = 0;
    result += readUInt8();
    result += readUInt8() << 8;
    return result;
  }

    /**
     * Reads i bits from the underlying buffers.
     * Caveat: this method always consumes full bytes even if just a bit is read!
     *
     * @param i number of bits to read, 31 max
     * @return bitstring value as unsigned int
     */
  public int readBits(int i) {
    if (i > 31) {
      //> signed int
      throw new IllegalArgumentException("cannot read more than 31 bits");
    }
    
    int ret = 0;
    while (i > 8) {
      final int moved = parse8(8) << i - 8;
      ret = ret | moved;
      i -= 8;
    }
    return ret | parse8(i);
  }

  private int parse8(int i) {
    if (readBitsRemaining == 0) {
      readBitsBuffer = read();
      readBitsRemaining = 8;
    }

    if (i > readBitsRemaining) {
      final int resultRemaining = i - readBitsRemaining;
      int buffer = (readBitsBuffer & (int) (Math.pow(2, readBitsRemaining)  - 1)) << resultRemaining;

      readBitsBuffer = read();
      readBitsRemaining = 8 - resultRemaining;
      final int movedAndMasked = (readBitsBuffer >>> readBitsRemaining) & (int) (Math.pow(2, resultRemaining) - 1);
      return buffer | movedAndMasked;
    } else {
      readBitsRemaining -= i;

      return (readBitsBuffer >>> readBitsRemaining) & (int) (Math.pow(2, i) - 1);
    }
  }

  public int getReadBitsRemaining() {
    return readBitsRemaining;
  }
}
