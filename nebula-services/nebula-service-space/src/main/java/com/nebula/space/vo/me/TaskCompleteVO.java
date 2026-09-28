package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 完成结果：重复任务完成时附带生成的下一次
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompleteVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private TaskVO task;

    private TaskVO next;
}
