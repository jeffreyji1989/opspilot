package com.opspilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opspilot.entity.ServiceInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ServiceInstanceMapper extends BaseMapper<ServiceInstance> {

    /**
     * 更新实例进程状态
     */
    @Update("UPDATE t_service_instance SET process_status = #{status} WHERE id = #{id}")
    int updateProcessStatus(Long id, int status);

    /**
     * 更新当前版本
     */
    @Update("UPDATE t_service_instance SET current_version = #{version} WHERE id = #{id}")
    int updateCurrentVersion(Long id, String version);
}
