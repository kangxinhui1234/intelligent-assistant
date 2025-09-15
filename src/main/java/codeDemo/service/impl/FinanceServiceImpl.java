package service.impl;

import entity.Finance;
import mapper.FinanceMapper;
import service.IFinanceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author kxh
 * @since 2025-09-12
 */
@Service
public class FinanceServiceImpl extends ServiceImpl<FinanceMapper, Finance> implements IFinanceService {

}
