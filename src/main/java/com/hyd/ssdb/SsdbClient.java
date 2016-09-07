package com.hyd.ssdb;

import com.hyd.ssdb.conf.Cluster;
import com.hyd.ssdb.conf.Server;
import com.hyd.ssdb.conf.Sharding;
import com.hyd.ssdb.protocol.Response;
import com.hyd.ssdb.sharding.ConsistentHashSharding;
import com.hyd.ssdb.util.IdScore;
import com.hyd.ssdb.util.KeyValue;
import com.hyd.ssdb.util.Processor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 包含连接池的客户端类，对于一个 SSDB 服务器只需要创建一个 SsdbClient 客户端。
 * <p>
 * 应用关闭时，需要调用 {@link #close()} 方法释放资源。
 *
 * @author Yiding
 */
public class SsdbClient extends AbstractClient {

    /**
     * 构造方法
     *
     * @param host 服务器地址
     * @param port 服务器端口
     *
     * @throws SsdbException 如果连接服务器失败
     */
    public SsdbClient(String host, int port) throws SsdbException {
        super(new ConsistentHashSharding(Cluster.fromSingleServer(host, port)));
    }

    // 创建只连接到一台服务器的 SsdbClient 对象
    public SsdbClient(String host, int port, int timeoutSeconds) throws SsdbException {
        super(new ConsistentHashSharding(Cluster.fromSingleServer(host, port, timeoutSeconds)));
    }

    // 创建只连接到一台服务器的，带密码的 SsdbClient 对象
    public SsdbClient(String host, int port, int timeoutSeconds, String pass) throws SsdbException {
        super(new ConsistentHashSharding(Cluster.fromSingleServer(host, port, pass)));
    }

    // 创建只连接到一台服务器的，带密码的 SsdbClient 对象
    public SsdbClient(String host, int port, String pass) throws SsdbException {
        super(new ConsistentHashSharding(Cluster.fromSingleServer(host, port, pass)));
    }

    // 基于一台服务器的配置创建 SsdbClient 对象
    public SsdbClient(Server server) {
        super(new ConsistentHashSharding(Cluster.fromSingleServer(server)));
    }

    // 创建连接到被 Sharding 托管的服务器集群的 SsdbClient 对象
    public SsdbClient(Sharding sharding) {
        super(sharding);
    }

    // 根据服务器列表，生成权重均等的基于一致性哈希环的分片集群，并创建连接到该集群的 SsdbClient 对象
    public SsdbClient(List<Server> servers) {
        super(new ConsistentHashSharding(Cluster.toClusters(servers)));
    }

    //////////////////////////////////////////////////////////////

    public SsdbClient(String host, int port, String pass, int soTimeout, int poolMaxTotal) throws SsdbException {
        super(new ConsistentHashSharding(new Cluster(new Server(host, port, pass, true, soTimeout, poolMaxTotal))));
    }

    public static SsdbClient fromSingleCluster(List<Server> servers) {
        return new SsdbClient(servers);
    }

    public static SsdbClient fromClusters(List<Cluster> clusters) {
        return new SsdbClient(new ConsistentHashSharding(clusters));
    }

    //////////////////////////////////////////////////////////////
    /**
     * 返回当前数据库的 key 的数量。
     * @return long
     */
    public long dbsize() {
        Response response = sendRequest("dbsize");
        return Long.parseLong(response.firstBlock());
    }
    /**
     * 以一种易于解释（parse）且易于阅读的格式，返回关于 Redis 服务器的各种信息和统计数值。包括主机参数，内存参数
     * @return
     */
    public String info() {
        Response response = sendRequest("info");
        return response.joinBlocks('\n');
    }

    //////////////////////////////////////////////////////////////// key value commands

    /**
     * 返回 key 所关联的字符串值。当 key 不存在时，返回 null ，否则，返回 key 的值。如果 key 不是字符串类型，那么返回一个错误。
     */
    public String get(String key) {
        return sendRequest("get", key).firstBlock();
    }
    /**
     *  返回 key 所关联的字符串值。当 key 不存在时，返回null
     * @param key
     * @return
     */
    public byte[] getBytes(String key) {
        return sendRequest("get", key).getBytes();
    }

    public void set(String key, Object value) {
        if (value == null) {
            throw new SsdbException("Cannot save null to SSDB");
        }

        sendWriteRequest("set", key, value);
    }
    /**
     * 将给定 key 的值设为 value ，并返回 key 的旧值(old value)。当 key 没有旧值时，也即是， key 不存在时，返回 null 。
     * @param key
     * @param value
     * @return
     */
    public String getset(String key, Object value) {
        if (value == null) {
            throw new SsdbException("Cannot save null to SSDB");
        }

        return sendWriteRequest("getset", key, value).firstBlock();
    }

    public void setx(String key, Object value, int ttlSeconds) {
        if (value == null) {
            throw new SsdbException("Cannot save null to SSDB");
        }

        sendWriteRequest("setx", key, value, ttlSeconds);
    }
    /**
     * 将 key 的值设为 value ，当且仅当 key 不存在。若给定的 key 已经存在，则 SETNX 不做任何动作。
     * @param key
     * @param value
     * @return
     */
    public int setnx(String key, Object value) {
        if (value == null) {
            throw new SsdbException("Cannot save null to SSDB");
        }

        return sendWriteRequest("setnx", key, value).getIntResult();
    }
    /**
     * 设置key的生命周期,秒级别
     * 
     */
    public int expire(String key, int ttlSeconds) {
        return sendWriteRequest("expire", key, ttlSeconds).getIntResult();
    }
    /**
     * 以秒为单位，返回给定key的剩余生命周期
     * 当 key 不存在时，返回 -2 。当 key 存在但没有设置剩余生存时间时，返回 -1 。否则，以秒为单位，返回 key 的剩余生存时间。
     * @param key
     * @return
     */
    public int ttl(String key) {
        return sendRequest("ttl", key).getIntResult();
    }

    /**
     * 删除一个或多个 key，注意这个方法对 zlist 无效，zlist 需要调用
     * {@link #zclear(String)} 方法
     *
     * @param keys 要删除的 key
     */
    public void del(String... keys) {
        if (keys.length == 1) {
            sendWriteRequest("del", keys[0]);
        } else if (keys.length > 1) {
            sendWriteRequest((Object[]) prependCommand("multi_del", keys));
        }
    }
    /**
     * 删除一个或多个 key，注意这个方法对 zlist 无效，zlist 需要调用
     * {@link #zclear(String)} 方法
     *
     * @param keys 要删除的 key
     */
    public void del(List<String> keys) {
        if (keys.size() == 1) {
            sendWriteRequest("del", keys.get(0));
        } else {
            sendWriteRequest((Object[]) prependCommand("multi_del", keys.toArray(new String[keys.size()])));
        }
    }
    /**
     * 将 key 中储存的数字值增一。
		如果 key 不存在，那么 key 的值会先被初始化为 0 ，然后再执行 INCR 操作。
		如果值包含错误的类型，或字符串类型的值不能表示为数字，那么返回一个错误。
     * @param key
     * @return
     */
    public long incr(String key) {
        return incr(key, 1);
    }
    /**
     * 将 key 中储存的数字值增 incr个数字。
		如果 key 不存在，那么 key 的值会先被初始化为 0 ，然后再执行 INCR 操作。
		如果值包含错误的类型，或字符串类型的值不能表示为数字，那么返回一个错误。
     * @param key
     * @return
     */
    public long incr(String key, long incr) {
        return sendWriteRequest("incr", key, incr).getLongResult();
    }
    /**
     * 检查给定 key 是否存在。
     * @param key
     * @return
     */
    public boolean exists(String key) {
        return sendRequest("exists", key).getIntResult() > 0;
    }

    public int getbit(String key, long offset) {
        // TO-not-DO ssdb 的这个命令有 BUG，返回值的顺序是反的
        // 这个问题已经无法修复了
        return sendRequest("getbit", key, offset).getIntResult();
    }

    public int setbit(String key, long offset) {
        if (offset > Restrictions.MAX_BIT_OFFSET) {
            throw new SsdbException("Offset too large (>" + Restrictions.MAX_BIT_OFFSET + ")");
        }

        return sendWriteRequest("setbit", key, offset).getIntResult();
    }

    public int bitcount(String key, long start, long end) {
        if (start > Restrictions.MAX_BIT_OFFSET) {
            throw new SsdbException("Start offset too large (>" + Restrictions.MAX_BIT_OFFSET + ")");
        }
        if (end > Restrictions.MAX_BIT_OFFSET) {
            throw new SsdbException("End offset too large (>" + Restrictions.MAX_BIT_OFFSET + ")");
        }
        return sendRequest("bitcount", key, start, end).getIntResult();
    }

    public int countbit(String key, long start, long end) {
        if (start > Restrictions.MAX_BIT_OFFSET) {
            throw new SsdbException("Start offset too large (>" + Restrictions.MAX_BIT_OFFSET + ")");
        }
        if (end > Restrictions.MAX_BIT_OFFSET) {
            throw new SsdbException("End offset too large (>" + Restrictions.MAX_BIT_OFFSET + ")");
        }
        return sendRequest("countbit", key, start, end).getIntResult();
    }

    public String substr(String key, int start, int size) {
        return sendRequest("substr", key, start, size).firstBlock();
    }

    public String substr(String key, int start) {
        return sendRequest("substr", key, start).firstBlock();
    }
    /**
     * 返回 key 所储存的字符串值的长度。
		当 key 储存的不是字符串值时，返回一个错误。
     * @param key
     * @return
     */
    public int strlen(String key) {
        return sendRequest("strlen", key).getIntResult();
    }
    /**
     * 查找所有符合给定模式 pattern 的 key 。
		KEYS * 匹配数据库中所有 key 。
		KEYS h?llo 匹配 hello ， hallo 和 hxllo 等。
		KEYS h*llo 匹配 hllo 和 heeeeello 等。
		KEYS h[ae]llo 匹配 hello 和 hallo ，但不匹配 hillo 。
		特殊符号用 \ 隔开
     * @param startExclude
     * @param endInclude
     * @param limit
     * @return
     */
    public List<String> keys(String startExclude, String endInclude, int limit) {
        return sendRequest("keys", startExclude, endInclude, limit).getBlocks();
    }

    public List<String> rkeys(String startExclude, String endInclude, int limit) {
        return sendRequest("rkeys", startExclude, endInclude, limit).getBlocks();
    }

    public List<KeyValue> scan(String startExclude, String endInclude, int limit) {
        return sendRequest("scan", startExclude, endInclude, limit).getKeyValues();
    }

    public void scan(String prefix, int batchSize, Processor<KeyValue> keyConsumer) {
        String start = prefix;
        String end = prefix + (char) 255;

        List<KeyValue> result = scan(start, end, batchSize);

        while (!result.isEmpty() && start.startsWith(prefix)) {
            for (KeyValue keyValue : result) {
                keyConsumer.process(keyValue);
            }
            start = result.get(result.size() - 1).getKey();
            result = scan(start, end, batchSize);
        }
    }

    public List<KeyValue> rscan(String startExclude, String endInclude, int limit) {
        return sendRequest("rscan", startExclude, endInclude, limit).getKeyValues();
    }

    public void multiSet(String... keyValues) {

        if (keyValues == null || keyValues.length == 0) {
            return;
        }

        if (keyValues.length % 2 == 1) {
            throw new SsdbException("Length of parameters must be odd");
        }

        String[] command = prependCommand("multi_set", keyValues);
        sendWriteRequest((Object[]) command);
    }

    public void multiSet(List<KeyValue> keyValues) {

        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }

        String[] command = new String[keyValues.size() * 2 + 1];
        command[0] = "multi_set";

        for (int i = 0; i < keyValues.size(); i++) {
            KeyValue keyValue = keyValues.get(i);
            command[i * 2 + 1] = keyValue.getKey();
            command[i * 2 + 2] = keyValue.getValue();
        }

        sendWriteRequest((Object[]) command);
    }

    //////////////////////////////////////////////////////////////// hashmap commands
    /**
     * 将哈希表 key 中的域 field 的值设为 value 。如果 key 不存在，一个新的哈希表被创建并进行 HSET 操作。如果域 field 已经存在于哈希表中，旧值将被覆盖。
     * 如果 field 是哈希表中的一个新建域，并且值设置成功，返回 1 。如果哈希表中域 field 已经存在且旧值已被新值覆盖，返回 0 。
     * @param key
     * @param propName
     * @param propValue
     */
    public void hset(String key, String propName, String propValue) {
        sendWriteRequest("hset", key, propName, propValue);
    }
    /**
  	 *将哈希表 key 中的域 field 的值设为 value 。如果 key 不存在，一个新的哈希表被创建并进行 HSET 操作。如果域 field 已经存在于哈希表中，旧值将被覆盖。
     * 如果 field 是哈希表中的一个新建域，并且值设置成功，返回 1 。如果哈希表中域 field 已经存在且旧值已被新值覆盖，返回 0 。
     * @param key
     * @param propName
     * @param propValue
     */
    
    public void hset(String key, String propName, Object propValue) {
        sendWriteRequest("hset", key, propName, propValue);
    }
    /**
     * 返回哈希表 key 中给定域 field 的值。当给定域不存在或是给定 key 不存在时，返回 null 。
     * @param key
     * @param propName
     * @return
     */
    public String hget(String key, String propName) {
        return sendRequest("hget", key, propName).firstBlock();
    }
    /**
     * 删除哈希表 key 中的一个或多个指定域，不存在的域将被忽略。
     * @param key
     * @param propName
     * @return 被成功移除的域的数量，不包括被忽略的域。
     */
    public int hdel(String key, String propName) {
        return sendWriteRequest("hdel", key, propName).getIntResult();
    }

    public long hincr(String key, String propName) {
        return hincr(key, propName, 1);
    }
    /**
     * 为哈希表 key 中的域 field 的值加上增量 increment 。
		增量也可以为负数，相当于对给定域进行减法操作。
		如果 key 不存在，一个新的哈希表被创建并执行 HINCRBY 命令。
		如果域 field 不存在，那么在执行命令前，域的值被初始化为 0 。
		对一个储存字符串值的域 field 执行 HINCRBY 命令将造成一个错误。
		本操作的值被限制在 64 位(bit)有符号数字表示之内。
     * @param key
     * @param propName
     * @param incr
     * @return 执行 HINCRBY 命令之后，哈希表 key 中域 field 的值。
     */
    public long hincr(String key, String propName, long incr) {
        return sendWriteRequest("hincr", key, propName, incr).getLongResult();
    }
    /**
     * 查看哈希表 key 中，给定域 field 是否存在。
     * @param key
     * @param propName
     * @return
     */
    public boolean hexists(String key, String propName) {
        return sendRequest("hexists", key, propName).getIntResult() > 0;
    }
    /**
     * 返回hash表长度。
     * @param key
     * @return
     */
    public int hsize(String key) {
        return sendRequest("hsize", key).getIntResult();
    }

    public List<KeyValue> hlist(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("hlist", key, startExclude, endInclude, limit).getKeyValues();
    }

    public List<KeyValue> hrlist(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("hrlist", key, startExclude, endInclude, limit).getKeyValues();
    }

    public List<String> hkeys(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("hkeys", key, startExclude, endInclude, limit).getBlocks();
    }

    public List<KeyValue> hgetall(String key) {
        return sendRequest("hgtall", key).getKeyValues();
    }

    public Map<String, String> hgetallmap(String key) {
        return sendRequest("hgtall", key).getBlocksAsMap();
    }

    public List<KeyValue> hscan(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("hscan", key, startExclude, endInclude, limit).getKeyValues();
    }

    public List<KeyValue> hrscan(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("hrscan", key, startExclude, endInclude, limit).getKeyValues();
    }

    public int hclear(String key) {
        return sendWriteRequest("hclear", key).getIntResult();
    }

    public void multiHset(String key, String... props) {
        if (props.length % 2 == 1) {
            throw new SsdbException("Length of props must be odd");
        }

        String[] command = prependCommand("multi_hset", key, props);
        sendWriteRequest((Object[]) command);
    }

    public void multiHset(String key, List<KeyValue> props) {
        sendWriteRequest((Object[]) prependCommandKeyValue("multi_hset", key, props));
    }

    public List<KeyValue> multiHget(String key, String... propNames) {
        return multiHget(key, Arrays.asList(propNames));
    }

    public List<KeyValue> multiHget(String key, List<String> propNames) {
        return sendRequest((Object[]) prependCommand("multi_hget", key, propNames)).getKeyValues();
    }

    public void multiHdel(String key, String... propNames) {
        sendWriteRequest((Object[]) prependCommand("multi_hdel", key, propNames));
    }

    //////////////////////////////////////////////////////////////// sorted set

    public void zset(String key, String id, long score) {
        sendWriteRequest("zset", key, id, score);
    }

    public long zget(String key, String id) {
        return sendRequest("zget", key, id).getLongResult();
    }

    public void zdel(String key, String id) {
        sendWriteRequest("zdel", key, id);
    }

    public long zincr(String key, String id, long incr) {
        return sendWriteRequest("zincr", key, id, incr).getLongResult();
    }

    public int zsize(String key) {
        return sendRequest("zsize", key).getIntResult();
    }

    public int zclear(String key) {
        return sendWriteRequest("zclear", key).getIntResult();
    }

    public List<String> zlist(String startExclude, String endInclude, int limit) {
        return sendRequest("zlist", startExclude, endInclude, limit).getBlocks();
    }

    public List<String> zkeys(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("zkeys", key, startExclude, endInclude, limit).getBlocks();
    }

    public List<IdScore> zscan(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("zscan", key, startExclude, endInclude, limit).getIdScores();
    }

    public List<IdScore> zrscan(String key, String startExclude, String endInclude, int limit) {
        return sendRequest("zrscan", key, startExclude, endInclude, limit).getIdScores();
    }

    /**
     * 获取 id 的排名，从小到大，0 表示第一位
     *
     * @param key id 所处的 zset 的 key
     * @param id  id
     *
     * @return 排名，如果 id 不在 key 当中则返回 -1
     */
    public int zrank(String key, String id) {
        return sendRequest("zrank", key, id).getIntResult();
    }

    // 同上，从大到小排名
    public int zrrank(String key, String id) {
        return sendRequest("zrrank", key, id).getIntResult();
    }

    public List<IdScore> zrange(String key, int offset, int limit) {
        return sendRequest("zrange", key, offset, limit).getIdScores();
    }

    public List<IdScore> zrrange(String key, int offset, int limit) {
        return sendRequest("zrrange", key, offset, limit).getIdScores();
    }

    /**
     * 查询 score 在 minScoreInclude 与 maxScoreInclude 之间的 id 数量
     *
     * @param key             zset 的 key
     * @param minScoreInclude score 最小值（含），Integer.MIN_VALUE 表示无最小值
     * @param maxScoreInclude score 最大值（含），Integer.MAX_VALUE 表示无最大值
     *
     * @return score 在 minScoreInclude 与 maxScoreInclude 之间的 id 数量
     */
    public int zcount(String key, int minScoreInclude, int maxScoreInclude) {
        String strMin = minScoreInclude == Integer.MIN_VALUE ? "" : String.valueOf(minScoreInclude);
        String strMax = maxScoreInclude == Integer.MAX_VALUE ? "" : String.valueOf(maxScoreInclude);
        return sendRequest("zcount", key, strMin, strMax).getIntResult();
    }

    public long zsum(String key, int minScoreInclude, int maxScoreInclude) {
        String strMin = minScoreInclude == Integer.MIN_VALUE ? "" : String.valueOf(minScoreInclude);
        String strMax = maxScoreInclude == Integer.MAX_VALUE ? "" : String.valueOf(maxScoreInclude);
        return sendRequest("zsum", key, strMin, strMax).getLongResult();
    }

    public long zavg(String key, int minScoreInclude, int maxScoreInclude) {
        String strMin = minScoreInclude == Integer.MIN_VALUE ? "" : String.valueOf(minScoreInclude);
        String strMax = maxScoreInclude == Integer.MAX_VALUE ? "" : String.valueOf(maxScoreInclude);
        return sendRequest("zavg", key, strMin, strMax).getLongResult();
    }

    /**
     * 删除指定排名范围内的 id
     *
     * @param key            zset 的 key
     * @param minRankInclude 最小排名（含），最小值为 0
     * @param maxRankInclude 最大排名（含），最大值为 zset 的大小
     *
     * @return 被删除的 id 的数量
     */
    public int zremrangebyrank(String key, int minRankInclude, int maxRankInclude) {
        return sendWriteRequest("zremrangebyrank", key, minRankInclude, maxRankInclude).getIntResult();
    }

    public int zremrangebyscore(String key, int minScoreInclude, int maxScoreInclude) {
        String strMin = minScoreInclude == Integer.MIN_VALUE ? "" : String.valueOf(minScoreInclude);
        String strMax = maxScoreInclude == Integer.MAX_VALUE ? "" : String.valueOf(maxScoreInclude);
        return sendWriteRequest("zremrangebyscore", key, strMin, strMax).getIntResult();
    }

    public List<IdScore> zpopFront(String key, int limit) {
        return sendWriteRequest("zpop_front", key, limit).getIdScores();
    }

    public List<IdScore> zpopBack(String key, int limit) {
        return sendWriteRequest("zpop_back", key, limit).getIdScores();
    }

    public void multiZset(String key, List<IdScore> idScores) {
        sendWriteRequest(prependCommandIdScore("multi_zset", key, idScores));
    }

    public List<IdScore> multiZget(String key, List<String> ids) {
        return sendRequest(prependCommand("multi_zget", key, ids)).getIdScores();
    }

    public void multiZdel(String key, List<String> ids) {
        sendWriteRequest(prependCommand("multi_zdel", key, ids));
    }

    //////////////////////////////////////////////////////////////

    public int qpushFront(String key, String... values) {
        return sendWriteRequest(prependCommand("qpush_front", key, values)).getIntResult();
    }

    public int qpushFront(String key, byte[] bytes) {
        return sendWriteRequest("qpush_front", key, bytes).getIntResult();
    }

    public int qpushBack(String key, String... values) {
        return sendWriteRequest(prependCommand("qpush_back", key, values)).getIntResult();
    }

    public int qpushBack(String key, byte[] bytes) {
        return sendWriteRequest("qpush_back", key, bytes).getIntResult();
    }

    public List<String> qpopFront(String key, int size) {
        return sendWriteRequest("qpop_front", key, size).getBlocks();
    }

    public List<String> qpopBack(String key, int size) {
        return sendWriteRequest("qpop_back", key, size).getBlocks();
    }

    public void qpopAllFront(String key, int batchSize, Processor<String> valueProcessor) {
        while (qsize(key) > 0) {
            List<String> values = qpopFront(key, batchSize);
            for (String value : values) {
                valueProcessor.process(value);
            }
        }
    }

    public void qpopAllBack(String key, int batchSize, Processor<String> valueProcessor) {
        while (qsize(key) > 0) {
            List<String> values = qpopBack(key, batchSize);
            for (String value : values) {
                valueProcessor.process(value);
            }
        }
    }

    public String qfront(String key) {
        return sendRequest("qfront", key).firstBlock();
    }

    public String qback(String key) {
        return sendRequest("qback", key).firstBlock();
    }

    public int qsize(String key) {
        return sendRequest("qsize", key).getIntResult();
    }

    public void qclear(String key) {
        sendWriteRequest("qclear", key);
    }

    public String qget(String key, int index) {
        return sendRequest("qget", key, index).firstBlock();
    }

    public byte[] qgetBytes(String key, int index) {
        return sendRequest("qget", key, index).getBytes();
    }

    public void qset(String key, int index, String value) {
        sendWriteRequest("qset", key, index, value);
    }

    public List<String> qrange(String key, int offset, int limit) {
        return sendRequest("qrange", key, offset, limit).getBlocks();
    }

    public List<String> qslice(String key, int startInclude, int endInclude) {
        return sendRequest("qslice", key, startInclude, endInclude).getBlocks();
    }

    public int qtrimFront(String key, int size) {
        return sendWriteRequest("qtrim_front", key, size).getIntResult();
    }

    public int qtrimBack(String key, int size) {
        return sendWriteRequest("qtrim_back", key, size).getIntResult();
    }

    /**
     * 列出指定区间的 queue/list 的 key 列表
     *
     * @param startKeyExclude 起始名字（不含，可选）
     * @param endKeyInclude   结束名字（含，可选）
     * @param limit           最多返回记录数
     *
     * @return 指定区间的 queue/list 的 key 列表
     */
    public List<String> qlist(String startKeyExclude, String endKeyInclude, int limit) {
        return sendRequest("qlist", startKeyExclude, endKeyInclude, limit).getBlocks();
    }

    public List<String> qrlist(String startKeyExclude, String endKeyInclude, int limit) {
        return sendRequest("qrlist", startKeyExclude, endKeyInclude, limit).getBlocks();
    }
}
