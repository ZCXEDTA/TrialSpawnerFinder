package cn.trialfinder.sim.json;

import cn.trialfinder.sim.data.PoolAliasBinding;
import cn.trialfinder.sim.data.TrialChambersData;
import cn.trialfinder.sim.pool.PoolRegistry;
import cn.trialfinder.sim.resource.ClasspathResourceLoader;
import cn.trialfinder.sim.resource.Identifier;
import cn.trialfinder.sim.resource.ResourceKey;
import cn.trialfinder.sim.template.StructureTemplateManager;
import cn.trialfinder.sim.pool.StructureTemplatePool;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 验证自研 JSON 解析器能加载真实试炼密室数据。 */
class JsonDataLoadTest {

    @Test
    void parsesAllPoolJsonFiles() {
        int parsed = 0;
        for (String resource : ClasspathResourceLoader.listResourcePaths(
                "data/minecraft/worldgen/template_pool", ".json")) {
            try (InputStream stream = ClasspathResourceLoader.open(resource)) {
                String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                Json.Object root = (Json.Object) Json.parse(text);
                assertTrue(root.getString("fallback").length() > 0, resource);
                parsed++;
            } catch (Exception e) {
                fail("解析失败: " + resource + " -> " + e, e);
            }
        }
        System.out.println("成功解析 pool JSON: " + parsed + " 个");
    }

    @Test
    void loadsAllPoolsThroughRegistry() {
        PoolRegistry registry = new PoolRegistry(new StructureTemplateManager());
        registry.loadAll();
        int poolCount = registry.pools().size();
        // 47 个 pool JSON + 1 个 empty 哨兵
        assertEquals(48, poolCount, "应注册 47 个 JSON 池 + empty，实际 " + poolCount);
        assertTrue(registry.pools().containsKey(
                        ResourceKey.create("trial_chambers/chamber/end")),
                "缺少起始池 trial_chambers/chamber/end");
    }

    @Test
    void loadsAliasesFromStructureJson() {
        List<PoolAliasBinding> aliases = TrialChambersData.aliases();
        // structure/trial_chambers.json 的 pool_aliases：1 random_group + 1 random(melee) + 1 random(small_melee)
        assertEquals(3, aliases.size(), "pool_aliases 应为 3 个，实际 " + aliases.size());
        System.out.println("别名类型: " + aliases.stream()
                .map(a -> a.getClass().getSimpleName()).toList());
    }
}
