package com.codingchili.Model;

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
    private static final int MAX_LINE_LENGTH = 16384;
    private static final int PAGE_16MB = 16777216;

    private static final char TOKEN_NULL = '\0';
    private static final char TOKEN_CR = '\r';
    private static final char TOKEN_LF = '\n';
    private static final char TOKEN_QUOTE = '\"';
    private static final char TOKEN_SEPARATOR = ',';

    private ByteBuffer buffer = ByteBuffer.allocate(MAX_LINE_LENGTH);
    private JsonObject headers = new JsonObject();
    private Iterator<String> header;
    private RandomAccessFile file;
    private MappedByteBuffer map;
    private long fileSize;
    private int index = 0;
    private int rows = 0;

    @Override
    public void setFileData(String localFileName, int offset, String fileName) throws FileNotFoundException {
        file = new RandomAccessFile(localFileName, "rw");
        try {
            map = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, PAGE_16MB);
            fileSize = file.length();
            readRowCount();
            readHeaders();
        } catch (IOException e) {
            throw new ParserException(e);
        }
    }

    @Override
    public Set<String> getSupportedFileExtensions() {
        return new HashSet<>(Collections.singletonList(".csv"));
    }

    @Override
    public void initialize() {
        index = 0;
        map.position(0);
        readRow(); // skip headers row.
        for (int i = 0; i < rows; i++) {
            readRow();
        }
    }

    private int readRowCount() {
        for (int i = map.position(); i < fileSize; i++) {
            if (map.get(i) == '\n') {
                rows++;
            }
        }
        return rows;
    }

    private void readHeaders() throws IOException {
        map.position(0);

        for (int i = map.position(); i < file.length(); i++) {
            if (map.get(i) == '\n') {
                Arrays.stream(new String(buffer.array()).split(","))
                        .map(header -> header.replaceAll("\"", ""))
                        .map(String::trim).forEach(header -> {
                    headers.put(header, "<empty>");
                });
                break;
            } else {
                buffer.put(map.get(i));
            }
        }
        buffer.clear();
    }

    private void process(AtomicInteger columnsRead, ByteBuffer buffer, JsonObject json) {
        columnsRead.incrementAndGet();

        if (columnsRead.get() > headers.size()) {
            throw new ColumnsExceededHeadersException(columnsRead.get(), headers.size(), index + 1);
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
        // reset current header.
        header = headers.fieldNames().iterator();

        AtomicInteger columnsRead = new AtomicInteger(0);
        JsonObject json = headers.copy();
        boolean quoted = false;
        boolean done = false;

        while (!done) {
            byte current = map.get();

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

        if (!(columnsRead.get() == headers.size())) {
            throw new ParserException(
                    String.format("Error at line %d, values (%d) does not match headers (%d).",
                            index, columnsRead.get(), headers.size()));
        } else {
            index++;
        }

        // parse json object.
        return json;
    }

    private Object parseDatatype(byte[] data) {
        String line = new String(data).trim();

        if (line.matches("[0-9]*")) {
            return Integer.parseInt(line);
        } else if (line.matches("true|false")) {
            return Boolean.parseBoolean(line);
        } else {
            return line;
        }
    }

    @Override
    public int getNumberOfElements() {
        return rows;
    }

    @Override
    public void subscribe(Subscriber<? super JsonObject> subscriber) {
        map.position(0);
        readRow();
        index = 0;

        subscriber.onSubscribe(new Subscription() {
            private boolean complete = false;
            private int index = 0;

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

                index += count;

                if (index >= rows && !complete) {
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
