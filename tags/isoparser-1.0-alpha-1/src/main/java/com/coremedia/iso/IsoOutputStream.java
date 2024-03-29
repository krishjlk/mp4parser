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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An output stream for easy writing of ISO boxes. May calculate DCF hashes.
 */
public class IsoOutputStream extends FilterOutputStream {
  private int streamPosition;

  private boolean hashCalculationStarted = true;
  private boolean calculateHash;
  private MessageDigest digest;

  public IsoOutputStream(OutputStream os, boolean calculateHash) {
    super(os);
    if (calculateHash) {
      try {
        digest = MessageDigest.getInstance("SHA1");
      } catch (NoSuchAlgorithmException e) {
        throw new Error(e);
      }
    }
    this.calculateHash = calculateHash;
  }

  /**
   * Hash calculation is activated by default. Boxes that are not used
   * to calculate the hash ('mdri' and children) will call this before
   * writing and will call {@link #startHashCalculation()} afterwards.
   */
  public void stopHashCalculation() {
    hashCalculationStarted = false;
  }

  /**
   * Hash calculation is activated by default. Boxes that are not used
   * to calculate the hash ('mdri' and children) will call {@link #stopHashCalculation()}
   * before writing and will call this method afterwards.
   */
  public void startHashCalculation() {
    hashCalculationStarted = false;
  }

  public long getStreamPosition() {
    return streamPosition;
  }

  public void writeUInt64(long uint64) throws IOException {
    writeUInt32((int) (uint64 >> 32));
    writeUInt32((int) uint64);
  }

  public void writeUInt32(long uint32) throws IOException {
    writeUInt16((int) (uint32 & 0xffff0000) >> 16);
    writeUInt16((int) uint32 & 0x0000ffff);
  }

  public void writeUInt24(int uint24) throws IOException {
    writeUInt16(uint24 >> 8);
    writeUInt8(uint24);
  }

  public void writeUInt16(int uint16) throws IOException {
    writeUInt8(uint16 >> 8);
    writeUInt8(uint16);
  }

  public void writeUInt8(int uint8) throws IOException {
    write(uint8 & 0xFF);
  }

  public void write(int b) throws IOException {
    out.write(b);
    streamPosition++;
    if (hashCalculationStarted && calculateHash) {
      digest.update((byte) b);
    }

  }

  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
    streamPosition += len;
    if (hashCalculationStarted && calculateHash) {
      digest.update(b, off, len);
    }
  }

  public void write(byte[] b) throws IOException {
    out.write(b);
    streamPosition += b.length;
    if (hashCalculationStarted && calculateHash) {
      digest.update(b);
    }
  }


  public void writeStringZeroTerm(String str) throws IOException {
    str = str == null ? "" : str;
    str += "\0";
    byte[] toBeWritten = str.getBytes("UTF-8");
    write(toBeWritten);
  }

  public void writeStringNoTerm(String str) throws IOException {
    if (str != null) {
      byte[] toBeWritten = str.getBytes("UTF-8");
      write(toBeWritten);
    }
  }


  public void writeIso639(String language) throws IOException {
    int bits = 0;
    for (int i = 0; i < 3; i++) {
      bits += (language.getBytes()[i] - 0x60) << (2 - i) * 5;
    }
    writeUInt16(bits);
  }

  public void writeFixedPont1616(double v) throws IOException {
    int result = (int) (v * 65536);
    write((result & 0xFF000000) >> 24);
    write((result & 0x00FF0000) >> 16);
    write((result & 0x0000FF00) >> 8);
    write((result & 0x000000FF));
  }

  public void writeFixedPont88(double v) throws IOException {
    short result = (short) (v * 256);
    write((result & 0xFF00) >> 8);
    write((result & 0x00FF));
  }

  public byte[] getHash() {
    if (calculateHash) {
      return digest.digest();
    } else {
      return null;
    }
  }
}
