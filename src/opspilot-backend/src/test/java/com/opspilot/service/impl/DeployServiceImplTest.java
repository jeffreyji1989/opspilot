package com.opspilot.service.impl;

import com.opspilot.common.Result;
import com.opspilot.common.SshManager;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.DeployRecordMapper;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 发版部署服务单元测试
 *
 * <p>测试 DeployServiceImpl 的参数校验、异常处理等核心逻辑。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
class DeployServiceImplTest {

    @Mock
    private DeployRecordMapper deployRecordMapper;

    @Mock
    private DeployStepMapper deployStepMapper;

    @Mock
    private ModuleMapper moduleMapper;

    @Mock
    private ServerMapper serverMapper;

    @Mock
    private ServiceInstanceMapper serviceInstanceMapper;

    @Mock
    private SshManager sshManager;

    @InjectMocks
    private DeployServiceImpl deployService;

    @BeforeEach
    void setUp() {
        // Use synchronous executor for testing so deploy runs in same thread
        deployService.deployTaskExecutor = new SyncTaskExecutor();
    }

    @Test
    void testDeploy_nullModuleId_returnsError() {
        Result<Long> result = deployService.deploy(null, 1L, "test");
        assertFalse(result.isSuccess());
        assertEquals("模块和实例不能为空", result.getMessage());
    }

    @Test
    void testDeploy_nullInstanceId_returnsError() {
        Result<Long> result = deployService.deploy(1L, null, "test");
        assertFalse(result.isSuccess());
        assertEquals("模块和实例不能为空", result.getMessage());
    }

    @Test
    void testDeploy_moduleNotFound_returnsError() {
        when(moduleMapper.selectById(1L)).thenReturn(null);

        Result<Long> result = deployService.deploy(1L, 1L, "test");
        assertFalse(result.isSuccess());
        assertEquals("模块不存在", result.getMessage());
    }

    @Test
    void testDeploy_instanceNotFound_returnsError() {
        Module module = new Module();
        module.setId(1L);
        module.setModuleName("test-module");
        when(moduleMapper.selectById(1L)).thenReturn(module);
        when(serviceInstanceMapper.selectById(1L)).thenReturn(null);

        Result<Long> result = deployService.deploy(1L, 1L, "test");
        assertFalse(result.isSuccess());
        assertEquals("实例不存在", result.getMessage());
    }

    @Test
    void testDeploy_instanceAlreadyDeploying_returnsError() {
        Module module = new Module();
        module.setId(1L);
        module.setModuleName("test-module");

        ServiceInstance instance = new ServiceInstance();
        instance.setId(1L);
        instance.setModuleId(1L);
        instance.setProcessStatus(1); // RUNNING

        when(moduleMapper.selectById(1L)).thenReturn(module);
        when(serviceInstanceMapper.selectById(1L)).thenReturn(instance);

        Result<Long> result = deployService.deploy(1L, 1L, "test");
        assertFalse(result.isSuccess());
        assertEquals("实例正在部署中，请稍后再试", result.getMessage());
        // Verify no deploy record was created
        verify(deployRecordMapper, never()).insert(any());
    }

    @Test
    void testGetProgress_recordNotFound_returnsError() {
        when(deployRecordMapper.selectById(999L)).thenReturn(null);

        Result<DeployRecord> result = deployService.getProgress(999L);
        assertFalse(result.isSuccess());
        assertEquals("部署记录不存在", result.getMessage());
    }

    @Test
    void testGetProgress_recordExists_returnsRecord() {
        DeployRecord record = new DeployRecord();
        record.setId(1L);
        record.setStatus(2); // SUCCESS
        record.setOperator("test");
        record.setVersion("v1.0");
        record.setCreatedTime(new Date());

        when(deployRecordMapper.selectById(1L)).thenReturn(record);

        Result<DeployRecord> result = deployService.getProgress(1L);
        assertTrue(result.isSuccess());
        assertEquals(1L, result.getData().getId());
        assertEquals("v1.0", result.getData().getVersion());
    }

    @Test
    void testGetHistory_returnsRecordList() {
        when(deployRecordMapper.selectByModuleId(1L)).thenReturn(java.util.Collections.emptyList());

        Result<java.util.List<DeployRecord>> result = deployService.getHistory(1L);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }
}
