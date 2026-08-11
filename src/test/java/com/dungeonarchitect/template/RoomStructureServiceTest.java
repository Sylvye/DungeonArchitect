package com.dungeonarchitect.template;

import org.bukkit.Server;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RoomStructureServiceTest {
    @Test
    void cachesAndInvalidatesLoadedStructures() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        Structure structure = proxy(Structure.class, (proxy, method, args) ->
            method.getName().equals("getSize") ? new BlockVector(4, 5, 6) : defaultValue(method.getReturnType()));
        StructureManager manager = proxy(StructureManager.class, (proxy, method, args) -> {
            if (method.getName().equals("loadStructure") && args.length == 1 && args[0] instanceof java.io.File) {
                loads.incrementAndGet();
                return structure;
            }
            return defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (proxy, method, args) ->
            method.getName().equals("getStructureManager") ? manager : defaultValue(method.getReturnType()));
        RoomStructureService service = new RoomStructureService(server);
        Path path = Path.of("build", "cache-test", "room.nbt");

        assertSame(service.loadStructure(path), service.loadStructure(path));
        assertEquals(new com.dungeonarchitect.domain.IntVector3(4, 5, 6), service.loadSize(path));
        assertEquals(1, loads.get());

        service.invalidate(path);
        service.loadStructure(path);
        assertEquals(2, loads.get());

        service.clearCache();
        service.loadStructure(path);
        assertEquals(3, loads.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
