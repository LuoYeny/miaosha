# miaosha
商城秒杀系统

第一阶段：实现基本的秒杀功能

第二阶段：基于分布式扩展

1.redis

在使用redis存储session时 每次刷新session都不同
经查询：升级springboot 2.x  ——跨域导致session问题
spring-session 2.x 中 Cookie里面引入了SameSite ，他默认值是 Lax 

　　SameSite Cookie 是用来防止CSRF攻击，它有两个值：Strict、Lax

SameSite = Strict：


　　意为严格模式，表明这个cookie在任何情况下都不可能作为第三方cookie；

SameSite = Lax：


　　意为宽松模式，在GET请求是可以作为第三方cookie，但是不能携带cookie进行跨域post访问（这就很蛋疼了，我们那个校验接口就是POST请求）

总结：前端请求到后台，每次session都不一样，每次都是新的会话，导致获取不到用户信息

解决方案：

　　将SameSite设置为空
``
