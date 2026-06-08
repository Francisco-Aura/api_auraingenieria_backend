package api.aura.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wraps every DataSource bean with AuraUserDataSource so that the
 * @aura_usuario_nombre session variable is set on every connection
 * checkout without requiring AOP or transaction interception.
 */
@Component
public class AuraDataSourceBeanPostProcessor implements BeanPostProcessor {

   @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof AuraUserDataSource)) {
            System.out.println(">>> Wrapping DataSource: " + beanName + " (" + bean.getClass().getName() + ")");
            return new AuraUserDataSource((DataSource) bean);
        }
        return bean;
    }
}
