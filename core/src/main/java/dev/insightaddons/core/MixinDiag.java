package dev.insightaddons.core;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 믹신 진단 플러그인. 믹신이 실제로 적용될 때마다 로그(INFO)로 남기고,
 * 게임 로딩 완료와 월드 첫 입장 때 "적용됨 / 아직 적용 안 됨" 요약을 찍습니다.
 * 대상 모드의 클래스는 처음 쓰일 때 로드되므로(예: 보관함 화면은 처음 열 때), 요약에서 "아직"인 항목은 대개 정상입니다.
 * 이 플러그인은 믹신 로딩 초기에 실행되므로 마인크래프트/FML 클래스를 쓰지 않습니다.
 */
public final class MixinDiag implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("insight_addons/MixinDiag");
    private static final String CONFIG = "insight_addons.mixins.json";
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private String pkg = "";

    @Override
    public void onLoad(String mixinPackage) {
        this.pkg = mixinPackage;
        LOG.info("믹신 설정 로드: {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (APPLIED.add(simple)) {
            LOG.info("믹신 적용됨: {} -> {}", simple, targetClassName);
        }
    }

    /** 믹신 설정 파일에 적힌 이름들 */
    private static List<String> declared() {
        List<String> out = new ArrayList<>();
        try (InputStream in = MixinDiag.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) {
                return out;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int a = json.indexOf("\"client\"");
            int b = a < 0 ? -1 : json.indexOf(']', a);
            if (a >= 0 && b > a) {
                Matcher m = Pattern.compile("\"([A-Za-z0-9_]+)\"").matcher(json.substring(json.indexOf('[', a), b));
                while (m.find()) {
                    out.add(m.group(1));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 지금까지의 적용 현황을 로그로 남깁니다. */
    public static void summary(String when) {
        List<String> all = declared();
        List<String> pending = new ArrayList<>();
        for (String m : all) {
            if (!APPLIED.contains(m)) {
                pending.add(m);
            }
        }
        LOG.info("[{}] 믹신 {}개 중 {}개 적용됨", when, all.size(), all.size() - pending.size());
        if (!pending.isEmpty()) {
            LOG.info("[{}] 아직 적용 안 된 믹신: {} (대상 모드가 없거나 그 화면/기능을 아직 안 열었다면 정상입니다. "
                    + "기능이 안 되는데 계속 여기에 있으면 대상 모드 버전이 바뀐 것일 수 있습니다)", when, pending);
        }
    }
}
