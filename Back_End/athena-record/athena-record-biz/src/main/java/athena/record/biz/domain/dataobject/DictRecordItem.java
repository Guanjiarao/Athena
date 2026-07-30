package athena.record.biz.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_record_item")
public class DictRecordItem {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String itemName;
    private String iconUrl;
    private Integer sort;
    private Integer modeType;
}
