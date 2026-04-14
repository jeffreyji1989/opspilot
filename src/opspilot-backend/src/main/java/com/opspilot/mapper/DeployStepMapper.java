package com.opspilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opspilot.entity.DeployStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 发版步骤 Mapper
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Mapper
public interface DeployStepMapper extends BaseMapper<DeployStep> {

    /**
     * 根据部署记录ID查询步骤列表
     */
    @Select("SELECT * FROM t_deploy_step WHERE deploy_record_id = #{recordId} ORDER BY step_order ASC")
    List<DeployStep> selectByRecordId(Long recordId);

    /**
     * 更新步骤状态
     */
    @Update("UPDATE t_deploy_step SET status = #{status} WHERE id = #{id}")
    int updateStatus(Long id, int status);

    /**
     * 更新步骤错误信息
     */
    @Update("UPDATE t_deploy_step SET error_message = #{message} WHERE id = #{id}")
    int updateErrorMessage(Long id, String message);
}
