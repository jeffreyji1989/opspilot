package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.entity.Module;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.ModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块服务实现
 *
 * <p>提供模块的增删改查、Maven 多模块构建命令自动拼装、Monorepo 子路径解析。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleServiceImpl extends ServiceImpl<ModuleMapper, Module> implements ModuleService {

    private final ServiceInstanceMapper serviceInstanceMapper;

    private static final Map<String, Map<String, String>> BUILD_TEMPLATES = Map.of(
            "JAR", Map.of("buildCommand", "mvn clean package -pl {mavenModuleName} -am -DskipTests",
                    "artifactPath", "target/{mavenModuleName}.jar", "buildTool", "maven", "runtimeType", "java"),
            "WAR", Map.of("buildCommand", "mvn clean package -pl {mavenModuleName} -am -DskipTests",
                    "artifactPath", "target/{mavenModuleName}.war", "buildTool", "maven", "runtimeType", "java"),
            "Vue", Map.of("buildCommand", "npm install && npm run build",
                    "artifactPath", "dist/", "buildTool", "npm", "runtimeType", "node"),
            "React", Map.of("buildCommand", "npm install && npm run build",
                    "artifactPath", "build/", "buildTool", "npm", "runtimeType", "node"),
            "Node.js", Map.of("buildCommand", "npm install",
                    "artifactPath", "", "buildTool", "npm", "runtimeType", "node"),
            "Android", Map.of("buildCommand", "./gradlew assembleRelease",
                    "artifactPath", "app/build/outputs/apk/release/", "buildTool", "gradle", "runtimeType", "java"),
            "Flutter", Map.of("buildCommand", "flutter build apk",
                    "artifactPath", "build/app/outputs/flutter-apk/", "buildTool", "flutter", "runtimeType", "flutter")
    );

    @Override
    public List<Module> listByProjectId(Long projectId) {
        return list(new LambdaQueryWrapper<Module>().eq(Module::getProjectId, projectId).orderByDesc(Module::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Module createModule(Module module) {
        if (module.getProjectId() == null) {
            throw new BusinessException("项目 ID 不能为空");
        }
        long count = count(new LambdaQueryWrapper<Module>()
                .eq(Module::getProjectId, module.getProjectId()).eq(Module::getModuleName, module.getModuleName()));
        if (count > 0) {
            throw new BusinessException("模块名称已存在");
        }
        assembleBuildCommand(module);
        save(module);
        log.info("模块创建成功: id={}, name={}", module.getId(), module.getModuleName());
        return module;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(Module module) {
        if (module.getId() == null || getById(module.getId()) == null) {
            throw new BusinessException("模块不存在");
        }
        Module existing = getById(module.getId());
        if (module.getModuleType() != null && !module.getModuleType().equals(existing.getModuleType())
                || !StringUtils.hasText(module.getBuildCommand())) {
            if (module.getModuleType() == null) module.setModuleType(existing.getModuleType());
            if (module.getMavenModuleName() == null) module.setMavenModuleName(existing.getMavenModuleName());
            assembleBuildCommand(module);
        }
        updateById(module);
        log.info("模块更新成功: id={}, name={}", module.getId(), module.getModuleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(Long id) {
        Module module = getById(id);
        if (module == null) {
            throw new BusinessException("模块不存在");
        }
        long serviceCount = serviceInstanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getModuleId, id));
        if (serviceCount > 0) {
            throw new BusinessException("该模块下存在服务实例，无法删除");
        }
        removeById(id);
        log.info("模块删除成功: id={}, name={}", id, module.getModuleName());
    }

    /** 数字模块类型到字符串键的映射 */
    private static final Map<String, String> MODULE_TYPE_MAP = Map.of(
            "0", "JAR", "1", "Vue", "2", "React", "3", "Node.js",
            "4", "WAR", "5", "Android", "6", "Flutter"
    );

    @Override
    public Map<String, String> getBuildTemplate(String moduleType) {
        if (!StringUtils.hasText(moduleType)) {
            throw new BusinessException("模块类型不能为空");
        }
        // 支持数字类型映射（如 0→JAR, 1→Vue）
        String resolvedType = MODULE_TYPE_MAP.getOrDefault(moduleType, moduleType);
        Map<String, String> template = BUILD_TEMPLATES.get(resolvedType);
        if (template == null) {
            throw new BusinessException("不支持的模块类型: " + moduleType);
        }
        return new HashMap<>(template);
    }

    /**
     * 自动拼装构建命令
     *
     * <p>Maven 多模块处理：如果配置了 mavenModuleName，拼装 -pl {mavenModuleName} -am 参数。
     * 否则使用默认 mvn clean package -DskipTests。</p>
     *
     * <p>Monorepo 子路径处理：如果配置了 repoPath，在构建命令前加 cd {repoPath} && 前缀，
     * 确保在正确的子路径下执行构建。</p>
     */
    private void assembleBuildCommand(Module module) {
        String moduleType = module.getModuleType();
        if (!StringUtils.hasText(moduleType)) {
            moduleType = "JAR";
        }
        // 支持数字类型映射
        moduleType = MODULE_TYPE_MAP.getOrDefault(moduleType, moduleType);

        Map<String, String> template = BUILD_TEMPLATES.get(moduleType);
        if (template == null) {
            throw new BusinessException("不支持的模块类型: " + module.getModuleType());
        }

        String buildCommand = template.get("buildCommand");
        String artifactPath = template.get("artifactPath");

        // Maven 多模块：替换占位符
        if ("maven".equals(template.get("buildTool"))) {
            String mavenModuleName = module.getMavenModuleName();
            if (StringUtils.hasText(mavenModuleName)) {
                buildCommand = buildCommand.replace("{mavenModuleName}", mavenModuleName);
                artifactPath = artifactPath.replace("{mavenModuleName}", mavenModuleName);
            } else {
                buildCommand = "mvn clean package -DskipTests";
                artifactPath = "target/" + module.getModuleName() + ".jar";
            }
        }

        // Monorepo 子路径：添加 cd 前缀
        if (StringUtils.hasText(module.getRepoPath())) {
            buildCommand = "cd " + module.getRepoPath() + " && " + buildCommand;
        }

        module.setBuildCommand(buildCommand);
        if (!StringUtils.hasText(module.getArtifactPath())) {
            module.setArtifactPath(artifactPath);
        }
        if (!StringUtils.hasText(module.getBuildTool())) {
            module.setBuildTool(template.get("buildTool"));
        }
    }
}
