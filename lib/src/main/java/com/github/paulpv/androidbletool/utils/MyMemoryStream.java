package com.github.paulpv.androidbletool.utils;

public class MyMemoryStream {
    public static final byte[] EMPTY_BUFFER = new byte[0];
    public static final int BLOCK_SIZE = 256;

    protected byte[] buffer = EMPTY_BUFFER; // never null
    private int position = 0;
    private int length = 0;

    public MyMemoryStream() {
        this(BLOCK_SIZE);
    }

    public MyMemoryStream(int capacity) {
        makeSpaceFor(capacity);
    }

    public synchronized void reset() {
        setLength(0);
        //setPosition(0);
    }

    public synchronized void clear() {
        this.buffer = EMPTY_BUFFER;
        reset();
    }

    public synchronized int getCapacity() {
        return this.buffer.length;
    }

    public synchronized byte[] getBuffer() {
        return this.buffer;
    }

    public synchronized int getPosition() {
        return this.position;
    }

    public synchronized void setPosition(int position) {
        makeSpaceFor(position);
        this.position = position;
    }

    public synchronized int incPosition(int amount) {
        setPosition(getPosition() + amount);
        return getPosition();
    }

    public synchronized int getLength() {
        return this.length;
    }

    public synchronized void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }

        makeSpaceFor(length);

        // this.buffer can contain previously used data
        // if length > this.length, re-zero any length added to the end.
        MyArraysPlatform.fill(this.buffer, (byte) 0, this.length, length);

        this.length = length;

        if (this.position > this.length) {
            this.position = this.length;
        }
    }

    public synchronized int incLength(int amount) {
        setLength(getLength() + amount);
        return getPosition();
    }

    protected synchronized boolean makeSpaceFor(int size) {
        if (size <= this.buffer.length) {
            // already big enough, do nothing
            // this also handles the size <= 0 case
            return false;
        }

        int remainder = size % BLOCK_SIZE;
        size = size / BLOCK_SIZE * BLOCK_SIZE;
        if (remainder > 0) {
            size += BLOCK_SIZE;
        }
        if (size == 0) {
            return false;
        }

        byte[] tmp = new byte[size];

        // only need to copy the bytes in the array that are actually used
        System.arraycopy(this.buffer, 0, tmp, 0, this.length);

        this.buffer = tmp;

        // this.position and this.length remain unchanged

        return true;
    }

    public synchronized void write(byte[] buffer, int offset, int length) {
        makeSpaceFor(this.position + length);
        System.arraycopy(buffer, offset, this.buffer, this.position, length);
        this.position += length;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeInt8(byte value) {
        makeSpaceFor(this.position + 1);
        this.buffer[this.position] = (byte) value;
        this.position += 1;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt8(short value) {
        if ((value >> 8) != 0) {
            throw new IllegalArgumentException("value is not a uint8: 0x" + Utils.toHexString(value, 4));
        }
        makeSpaceFor(this.position + 1);
        this.buffer[this.position] = (byte) value;
        this.position += 1;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeInt16(short value) {
        makeSpaceFor(this.position + 2);
        this.buffer[this.position] = (byte) (value >> 8);
        this.buffer[this.position + 1] = (byte) value;
        this.position += 2;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt16(int value) {
        if ((value >> 16) != 0) {
            throw new IllegalArgumentException("value is not a uint16: 0x" + Utils.toHexString(value, 8));
        }
        makeSpaceFor(this.position + 2);
        this.buffer[this.position] = (byte) (value >> 8);
        this.buffer[this.position + 1] = (byte) value;
        this.position += 2;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeInt32(long value) {
        makeSpaceFor(this.position + 4);
        this.buffer[this.position] = (byte) (value >> 24);
        this.buffer[this.position + 1] = (byte) (value >> 16);
        this.buffer[this.position + 2] = (byte) (value >> 8);
        this.buffer[this.position + 3] = (byte) value;
        this.position += 4;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeUInt32(long value) {
        if ((value >> 32) != 0) {
            throw new IllegalArgumentException("value is not a uint32: 0x" + Utils.toHexString(value, 16));
        }
        makeSpaceFor(this.position + 4);
        this.buffer[this.position] = (byte) (value >> 24);
        this.buffer[this.position + 1] = (byte) (value >> 16);
        this.buffer[this.position + 2] = (byte) (value >> 8);
        this.buffer[this.position + 3] = (byte) value;
        this.position += 4;
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    public synchronized void writeString(String value) {
        // TODO:(pv) Replace with or utilize Utils.getBytes()...
        if (value != null && value.length() > 0) {
            byte[] b = Utils.getBytes(value);
            makeSpaceFor(this.position + b.length + 1);
            write(b, 0, b.length);
        }
        writeUInt8((short) 0);
        if (this.position > this.length) {
            this.length = this.position;
        }
    }

    protected static boolean checkOffset(int size, byte[] buffer, int offset, int length) {
        return checkOffset(size, buffer, offset, length, true, true);
    }

    protected static boolean checkOffset(int size, byte[] buffer, int offset, int length, //
                                         boolean checkParameters, boolean throwException) {
        if (checkParameters) {
            if (buffer == null) {
                throw new IllegalArgumentException("buffer must not be null");
            }

            if (length > buffer.length) {
                throw new IllegalArgumentException(
                        "length(" + length + ") must be <= buffer.length(" + buffer.length + ")");
            }

            if (offset < 0 || offset >= length) {
                throw new IllegalArgumentException("offset(" + offset + ") must be >= 0 and < (length(" + length
                        + ") or buffer.length(" + buffer.length + "))");
            }
        }

        if (offset + size > length) {
            if (throwException) {
                throw new IndexOutOfBoundsException("attempted to read " + size + " bytes past offset(" + offset
                        + ") would exceed length(" + length + ")");
            }
            return false;
        }
        return true;
    }

    private static int unsignedByteToInt(byte value) {
        return (int) (value & 0xff);
    }

    private static int unsignedByteToInt(byte value, int leftShift) {
        return unsignedByteToInt(value) << leftShift;
    }

    public synchronized int read(byte[] dest, int offset, int count) {
        count = Math.min(count, this.length - this.position);
        System.arraycopy(this.buffer, this.position, dest, offset, count);
        this.position += count;
        return count;
    }

    public synchronized byte readInt8() {
        checkOffset(1, this.buffer, this.position, this.length);
        byte value = this.buffer[this.position++];
        return value;
    }

    public synchronized short readUInt8() {
        checkOffset(1, this.buffer, this.position, this.length);
        short value = (short) unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized short readInt16() {
        checkOffset(2, this.buffer, this.position, this.length);
        short value = (short) unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized int readUInt16() {
        checkOffset(2, this.buffer, this.position, this.length);
        int value = unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized int readInt32() {
        checkOffset(4, this.buffer, this.position, this.length);
        int value = unsignedByteToInt(this.buffer[this.position++], 24);
        value += unsignedByteToInt(this.buffer[this.position++], 16);
        value += unsignedByteToInt(this.buffer[this.position++], 8);
        value += unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized long readUInt32() {
        checkOffset(4, this.buffer, this.position, this.length);
        long value = (long) unsignedByteToInt(this.buffer[this.position++], 24);
        value += (long) unsignedByteToInt(this.buffer[this.position++], 16);
        value += (long) unsignedByteToInt(this.buffer[this.position++], 8);
        value += (long) unsignedByteToInt(this.buffer[this.position++]);
        return value;
    }

    public synchronized String readString() {
        int index = this.position;
        while (checkOffset(1, this.buffer, this.position, this.length) && this.buffer[this.position] != 0) {
            this.position++;
        }
        this.position++; // null terminated
        return Utils.getString(this.buffer, index, this.position - index - 1);
    }

    public synchronized String toDebugString() {
        StringBuffer sb = new StringBuffer();
        sb.append('(').append(this.length).append("):").append(Utils.toHexString(this.buffer, 0, this.length));
        return sb.toString();
    }

    public static byte[] getBytes(byte value) {
        byte[] bytes =
                {
                        (byte) (value & 0xFF),
                };
        return bytes;
    }

    public static byte[] getBytes(short value) {
        byte[] bytes =
                {
                        (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF),
                };
        return bytes;
    }

    public static byte[] getBytes(int value) {
        byte[] bytes =
                {
                        (byte) (value & 0xFF),
                        (byte) ((value >> 8) & 0xFF),
                        (byte) ((value >> 16) & 0xFF),
                        (byte) ((value >> 24) & 0xFF),
                };
        return bytes;
    }

    public static byte[] getBytes(long value) {
        byte[] bytes =
                {
                        (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) ((value >> 16) & 0xFF),
                        (byte) ((value >> 24) & 0xFF), (byte) ((value >> 32) & 0xFF), (byte) ((value >> 40) & 0xFF),
                        (byte) ((value >> 48) & 0xFF), (byte) ((value >> 56) & 0xFF),
                };
        return bytes;
    }
}
