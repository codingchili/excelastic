package com.codingchili.Model;

import com.codingchili.logging.ApplicationLogger;
import io.vertx.core.json.JsonObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Robin Duda
 * <p>
 * Parses CSV files.
 */
public class CSVParser implements FileParser {
    private static final int MAX_LINE_LENGTH = 524288;
    private static long MAP_SIZE = Integer.MAX_VALUE / 4;

    private static final char TOKEN_NULL = '\0';
    private static final char TOKEN_CR = '\r';
    private static final char TOKEN_LF = '\n';
    private static final char TOKEN_QUOTE = '\"';
    private static final char TOKEN_SEPARATOR = ',';

    private ApplicationLogger logger = new ApplicationLogger(getClass());
    private ByteBuffer buffer = ByteBuffer.allocate(MAX_LINE_LENGTH);
    private JsonObject headers = new JsonObject();
    private Iterator<String> header;
    private RandomAccessFile file;
    private MappedByteBuffer[] maps;
    private long fileSize;
    private long index = 0;
    private int rows = 0;
    private long row = 0;

    @Override
    public void setFileData(String localFileName, int _unused, String fileName) throws FileNotFoundException {
        file = new RandomAccessFile(localFileName, "r"); // don't open for writing: writes to file.
        FileChannel channel = file.getChannel();
        try {
            maps = new MappedByteBuffer[(int) (file.length() / MAP_SIZE) + 1];
            fileSize = file.length();

            for (int i = 0; i < maps.length; i++) {
                long offset = i * MAP_SIZE;
                long unmapped = Math.max(fileSize - (i * MAP_SIZE), 0);

                maps[i] = channel.map(FileChannel.MapMode.READ_ONLY,
                        offset,
                        Math.min(MAP_SIZE, unmapped));
            }

            readRowCount();
            readHeaders();

        } catch (Throwable e) {
            throw new ParserException(e, row);
        }
    }

    public static void setMaxMapSize(Integer bytes) {
        MAP_SIZE = bytes;
    }

    private byte get() {
        int page = (int) (index / MAP_SIZE);
        int offset = (int) (index - (page * MAP_SIZE));
        index++;
        return maps[page].get(offset);
    }

    private void reset() {
        index = 0;
        row = 0;
        for (MappedByteBuffer map : maps) {
            map.position(0);
        }
    }

    @Override
    public Set<String> getSupportedFileExtensions() {
        return new HashSet<>(Collections.singletonList(".csv"));
    }

    @Override
    public void initialize() {
        reset();

        readRow(); // skip headers row.
        for (int i = 0; i < rows; i++) {
            readRow();
        }
    }

    private void readRowCount() {
        reset();

        for (long i = 0; i < fileSize; i++) {
            if (get() == '\n') {
                rows++;
                row = rows;
            }
        }
    }

    private void readHeaders() {
        reset();

        for (long i = 0; i < fileSize; i++) {
            byte current = get();
            if (current == '\n') {
                Arrays.stream(new String(buffer.array()).split(","))
                        .map(header -> header.replaceAll("\"", ""))
                        .map(String::trim).forEach(header -> {
                    headers.put(header, "<empty>");
                });
                break;
            } else {
                buffer.put(current);
            }
        }
        buffer.clear();
    }

    private void process(AtomicInteger columnsRead, ByteBuffer buffer, JsonObject json) {
        columnsRead.incrementAndGet();

        if (columnsRead.get() > headers.size()) {
            throw new ColumnsExceededHeadersException(columnsRead.get(), headers.size(), row + 1);
        } else {
            int read = buffer.position();
            byte[] line = new byte[read + 1];

            buffer.position(0);
            buffer.get(line, 0, read);
            line[line.length - 1] = '\0';

            json.put(header.next(), parseDatatype(line));
            buffer.clear();
        }
    }

    private JsonObject readRow() {
        // reset header.
        header = headers.fieldNames().iterator();

        AtomicInteger columnsRead = new AtomicInteger(0);
        JsonObject json = headers.copy();
        boolean quoted = false;
        boolean done = false;

        for (long i = index; i < fileSize && !done; i++) {
            byte current = get();

            if (i == fileSize - 1) {
                // file fully read.
                buffer.put(current);
                process(columnsRead, buffer, json);
                done = true;
            } else {
                switch (current) {
                    case TOKEN_NULL:
                        // EOF call process.
                        process(columnsRead, buffer, json);
                        done = true;
                        break;
                    case TOKEN_CR:
                    case TOKEN_LF:
                        // final header is being read and EOL appears.
                        if (columnsRead.get() == headers.size() - 1) {
                            process(columnsRead, buffer, json);
                            done = true;
                            break;
                        } else {
                            // skip token if not all headers read.
                            continue;
                        }
                    case TOKEN_QUOTE:
                        // toggle quoted to support commas within quotes.
                        quoted = !quoted;
                        break;
                    case TOKEN_SEPARATOR:
                        if (!quoted) {
                            process(columnsRead, buffer, json);
                            break;
                        }
                    default:
                        // store the current token in the buffer until the column ends.
                        buffer.put(current);
                }
            }
        }

        if (!(columnsRead.get() == headers.size())) {
            throw new ParserException(
                    String.format("Error at line %d, values (%d) does not match headers (%d).",
                            index, columnsRead.get(), headers.size()));
        } else {
            row++;
        }

        // parse json object.
        return json;
    }

    private Object parseDatatype(byte[] data) {
        String line = new String(data).trim();

        if (line.length() > 0) {
            if (line.matches("[0-9]*")) {
                return Long.parseLong(line);
            } else if (line.matches("true|false")) {
                return Boolean.parseBoolean(line);
            } else {
                return line;
            }
        } else {
            return line;
        }
    }

    @Override
    public int getNumberOfElements() {
        return rows;
    }

    @Override
    public void free() {
        try {
            file.close();
        } catch (IOException e) {
            logger.onError(e);
        }
    }

    @Override
    public void subscribe(Subscriber<? super JsonObject> subscriber) {
        reset();
        readRow();

        subscriber.onSubscribe(new Subscription() {
            private boolean complete = false;

            @Override
            public void request(long count) {
                for (int i = 0; i < count && i < rows; i++) {
                    JsonObject result = readRow();

                    if (result != null) {
                        subscriber.onNext(result);
                    } else {
                        complete = true;
                        subscriber.onComplete();
                    }
                }

                row += count;

                if (row >= rows && !complete) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                // send no more items!
            }
        });
    }
}
