package com.opspilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opspilot.entity.DeployRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeployRecordMapper extends BaseMapper<DeployRecord> {

    /**
     * 根据模块ID查询部署历史
     */
    @Select("SELECT * FROM t_deploy_record WHERE module_id = #{moduleId} ORDER BY created_time DESC")
    List<DeployRecord> selectByModuleId(Long moduleId);

    /**
     * 更新部署记录状态
     */
    @Update("UPDATE t_deploy_record SET status = #{status} WHERE id = #{id}")
    int updateStatus(Long id, int status);
}
