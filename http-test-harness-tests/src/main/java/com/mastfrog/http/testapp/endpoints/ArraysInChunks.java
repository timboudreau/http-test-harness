/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.http.testapp.endpoints;

import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ResponseWriter;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.acteur.annotations.HttpCall;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent;
import com.mastfrog.acteur.preconditions.Path;
import com.mastfrog.http.testapp.endpoints.ArraysInChunks.ChunkRangeInfo;
import com.mastfrog.http.testapp.endpoints.ArraysInChunks.WriteChunkResponse;
import static com.mastfrog.mime.MimeType.JSON_UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall(scopeTypes = ChunkRangeInfo.class)
@Path("/arrayChunks")
@Methods(GET)
@ParametersMustBeNumbersIfPresent({"start", "count", "by"})
@Description("Emits a JSON array of numbers starting from 0 or the URL parameter "
        + "<code>start</code>, up to that number plus the URL parameter <code>count</code> "
        + "or a default of 1000, flushed in HTTP chunks of the URL parameter <code>by</code> "
        + "or 100.")
@Concluders(WriteChunkResponse.class)
public class ArraysInChunks extends Acteur {

    @Inject
    ArraysInChunks(HttpEvent evt) {
        long start = evt.uriQueryParameter("start", Long.class).orElse(0L);
        long count = evt.uriQueryParameter("count", Long.class).orElse(1000L);
        long by = evt.uriQueryParameter("by", Long.class).orElse(100L);
        next(new ChunkRangeInfo(start, count, by));
    }

    static class WriteChunkResponse extends Acteur {

        @Inject
        WriteChunkResponse(ChunkRangeInfo info) {
            add(CONTENT_TYPE, JSON_UTF_8.withCharset(US_ASCII));
            setResponseWriter(ChunkRangeWriter.class);
            ok();
        }
    }

    static class ChunkRangeWriter extends ResponseWriter {

        private final ByteBufAllocator alloc;
        private final ChunkRangeInfo info;

        @Inject
        ChunkRangeWriter(ChunkRangeInfo info, ByteBufAllocator alloc) {
            this.info = info;
            this.alloc = alloc;
        }

        @Override
        public Status write(Event<?> evt, Output out, int iteration) throws Exception {
            if (iteration == 0) {
                out.write(new byte[]{'['});
            }
            ByteBuf batch = alloc.buffer();
            boolean isLast = info.next(batch);
            out.write(batch);
            if (isLast) {
                out.write(new byte[]{']'});
            } else {
                out.write(new byte[]{'\n'});
            }
            return isLast ? Status.DONE : Status.NOT_DONE;
        }
    }

    static class ChunkRangeInfo {

        private final AtomicLong current = new AtomicLong();
        private final long end;
        private final long by;

        ChunkRangeInfo(long start, long count, long by) {
            current.set(start);
            end = start + count;
            this.by = by;
        }

        public boolean isDone() {
            return current.get() >= end;
        }

        public boolean next(ByteBuf buf) {
            long start = current.get();
            if (start >= end) {
                return false;
            }
            long stop = Math.min(start + by, end);
            for (long l = start; l < stop; l++) {
                String s = Long.toString(l);
                if (l > start) {
                    buf.writeByte(',');
                }
                ByteBufUtil.writeAscii(buf, s);
            }
            current.set(stop);
            return stop >= end;
        }
    }
}
