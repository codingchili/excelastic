package com.codingchili.Model;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * @author Robin Duda
 * <p>
 * This codec is used to transfer a {@link ExcelParser} reference over the local event bus.
 */
public class ImportEventCodec implements MessageCodec<ImportEvent, ImportEvent> {

    /**
     * Registers this as the default codec for the supported class {@link ImportEvent}.
     * @param vertx the vertx instance that contains the event bus to register the codec on.
     */
    public static void registerOn(Vertx vertx) {
        vertx.eventBus().registerDefaultCodec(ImportEvent.class, new ImportEventCodec());
    }

    @Override
    public void encodeToWire(Buffer buffer, ImportEvent fileParser) {
        throw new RuntimeException("Clustering not currently supported.");
    }

    @Override
    public ImportEvent decodeFromWire(int i, Buffer buffer) {
        throw new RuntimeException("Clustering not currently supported.");
    }

    @Override
    public ImportEvent transform(ImportEvent fileParser) {
        // pass the reference for local messages.
        return fileParser;
    }

    @Override
    public String name() {
        return ImportEventCodec.class.getName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
