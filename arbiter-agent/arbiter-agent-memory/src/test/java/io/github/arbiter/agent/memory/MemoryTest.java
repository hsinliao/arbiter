package io.github.arbiter.agent.memory;

import io.github.arbiter.agent.spi.invocation.InvocationContext;
import io.github.arbiter.agent.spi.memory.MemoryQuery;
import io.github.arbiter.agent.spi.memory.MemoryRecord;
import io.github.arbiter.agent.spi.memory.MemoryType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTest {

    @Test
    void ranksLexicallyRelevantMemory() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        InvocationContext context = InvocationContext.of("task", "user", null);
        store.upsert(MemoryRecord.text("user", MemoryType.LONG_TERM,
                "The deployment target is production."), context);
        store.upsert(MemoryRecord.text("user", MemoryType.LONG_TERM,
                "The user likes green tea."), context);

        var result = store.search(new MemoryQuery(
                "user", "production deployment", 1, Set.of(MemoryType.LONG_TERM)), context);

        assertEquals(1, result.size());
        assertTrue(result.get(0).record().content().contains("production"));
        assertTrue(result.get(0).score() > 0);
    }

    @Test
    void handlesRepeatedWordsInQueriesAndRecords() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        InvocationContext context = InvocationContext.of("task", "user", null);
        store.upsert(MemoryRecord.text("user", MemoryType.LONG_TERM,
                "deploy deploy production"), context);

        var result = store.search(new MemoryQuery(
                "user", "deploy deploy production", 1, Set.of(MemoryType.LONG_TERM)), context);

        assertEquals(1, result.size());
    }

    @Test
    void retrievesChineseTextUsingCharacterBigrams() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        InvocationContext context = InvocationContext.of("task", "user", null);
        store.upsert(MemoryRecord.text("user", MemoryType.LONG_TERM, "生产部署需要审批"), context);
        store.upsert(MemoryRecord.text("user", MemoryType.LONG_TERM, "用户喜欢绿茶"), context);

        var result = store.search(new MemoryQuery(
                "user", "部署环境", 1, Set.of(MemoryType.LONG_TERM)), context);

        assertEquals("生产部署需要审批", result.get(0).record().content());
    }

    @Test
    void enforcesShortTermWindowAndPromotesWorkingMemory() {
        TieredMemory memory = new TieredMemory(2, 10, new InMemoryMemoryStore());
        InvocationContext context = InvocationContext.of("task", "s", null);
        memory.upsert(MemoryRecord.text("s", MemoryType.SHORT_TERM, "one"), context);
        memory.upsert(MemoryRecord.text("s", MemoryType.SHORT_TERM, "two"), context);
        memory.upsert(MemoryRecord.text("s", MemoryType.SHORT_TERM, "three"), context);
        assertEquals(2, memory.shortTermSize("s", context));

        MemoryRecord working = MemoryRecord.text("s", MemoryType.WORKING, "durable fact");
        memory.upsert(working, context);
        memory.upsert(MemoryRecord.text("s", MemoryType.LONG_TERM, "durable fact"), context);
        assertTrue(memory.search(
                new MemoryQuery("s", "durable", 5, Set.of()), context).stream()
                .anyMatch(match -> match.record().type() == MemoryType.LONG_TERM));
    }
}
