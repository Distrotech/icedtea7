/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;


class ByteBufferAsCharBufferL                  // package-private
    extends CharBuffer
{



    protected final ByteBuffer bb;
    protected final int offset;



    ByteBufferAsCharBufferL(ByteBuffer bb) {   // package-private

        super(-1, 0,
              bb.remaining() >> 1,
              bb.remaining() >> 1);
        this.bb = bb;
        // enforce limit == capacity
        int cap = this.capacity();
        this.limit(cap);
        int pos = this.position();
        assert (pos <= cap);
        offset = pos;



    }

    ByteBufferAsCharBufferL(ByteBuffer bb,
                                     int mark, int pos, int lim, int cap,
                                     int off)
    {

        super(mark, pos, lim, cap);
        this.bb = bb;
        offset = off;



    }

    public CharBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << 1) + offset;
        assert (off >= 0);
        return new ByteBufferAsCharBufferL(bb, -1, 0, rem, rem, off);
    }

    public CharBuffer duplicate() {
        return new ByteBufferAsCharBufferL(bb,
                                                    this.markValue(),
                                                    this.position(),
                                                    this.limit(),
                                                    this.capacity(),
                                                    offset);
    }

    public CharBuffer asReadOnlyBuffer() {

        return new ByteBufferAsCharBufferRL(bb,
                                                 this.markValue(),
                                                 this.position(),
                                                 this.limit(),
                                                 this.capacity(),
                                                 offset);



    }



    protected int ix(int i) {
        return (i << 1) + offset;
    }

    public char get() {
        return Bits.getCharL(bb, ix(nextGetIndex()));
    }

    public char get(int i) {
        return Bits.getCharL(bb, ix(checkIndex(i)));
    }



    public CharBuffer put(char x) {

        Bits.putCharL(bb, ix(nextPutIndex()), x);
        return this;



    }

    public CharBuffer put(int i, char x) {

        Bits.putCharL(bb, ix(checkIndex(i)), x);
        return this;



    }

    public CharBuffer compact() {

        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        ByteBuffer db = bb.duplicate();
        db.limit(ix(lim));
        db.position(ix(0));
        ByteBuffer sb = db.slice();
        sb.position(pos << 1);
        sb.compact();
        position(rem);
        limit(capacity());
        return this;



    }

    public boolean isDirect() {
        return bb.isDirect();
    }

    public boolean isReadOnly() {
        return false;
    }



    public String toString(int start, int end) {
        if ((end > limit()) || (start > end))
            throw new IndexOutOfBoundsException();
        try {
            int len = end - start;
            char[] ca = new char[len];
            CharBuffer cb = CharBuffer.wrap(ca);
            CharBuffer db = this.duplicate();
            db.position(start);
            db.limit(end);
            cb.put(db);
            return new String(ca);
        } catch (StringIndexOutOfBoundsException x) {
            throw new IndexOutOfBoundsException();
        }
    }


    // --- Methods to support CharSequence ---

    public CharSequence subSequence(int start, int end) {
        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        pos = (pos <= lim ? pos : lim);
        int len = lim - pos;

        if ((start < 0) || (end > len) || (start > end))
            throw new IndexOutOfBoundsException();
        int sublen = end - start;
        int off = offset + ((pos + start) << 1);
        assert (off >= 0);
        return new ByteBufferAsCharBufferL(bb, -1, 0, sublen, sublen, off);
    }




    public ByteOrder order() {




        return ByteOrder.LITTLE_ENDIAN;

    }

}
