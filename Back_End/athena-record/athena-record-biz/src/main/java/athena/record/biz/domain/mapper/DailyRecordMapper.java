package athena.record.biz.domain.mapper;

import athena.record.biz.domain.dataobject.DailyRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyRecordMapper extends BaseMapper<DailyRecord> {
    
    // 获取某个月份中有记录的所有日期
    @Select("SELECT DISTINCT record_date FROM daily_record " +
            "WHERE user_id = #{userId} " +
            "AND record_date >= #{startDate} " +
            "AND record_date <= #{endDate}")
    List<LocalDate> getRecordedDatesInMonth(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}