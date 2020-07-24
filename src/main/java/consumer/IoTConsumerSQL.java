package consumer;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;


/**
 * iotStream: {"sensor_ts":1588617762605,"sensor_id":7,"sensor_0":59,"sensor_1":32,"sensor_2":84,"sensor_3":23,"sensor_4":56,"sensor_5":30,"sensor_6":46,"sensor_7":90,"sensor_8":64,"sensor_9":33,"sensor_10":49,"sensor_11":91}
 * Aggregation on "sensor_id"
 *
 * run:
 *    cd /opt/cloudera/parcels/FLINK &&
 *    ./bin/flink run -m yarn-cluster -c consumer.IoTConsumerFilter -ynm IoTConsumerFilter lib/flink/examples/streaming/streaming-flink-0.1-SNAPSHOT.jar localhost:9092
 *
 *    java -classpath streaming-flink-0.1-SNAPSHOT.jar consumer.IoTConsumerFilter
 *
 * @author Marcel Daeppen
 * @version 2020/07/11 12:14
 */

public class IoTConsumerSQL {

    private static String brokerURI = "localhost:9092";
    private static String dataQualityTable = "dataDetailTable";


    public static void main(String args[]) throws Exception {

        if( args.length == 1 ) {
            System.err.println("case 'customized URI':");
            brokerURI = args[0];
            System.err.println("arg URL: " + brokerURI);
        }else {
            System.err.println("case default");
            System.err.println("default URI: " + brokerURI);
        }

        String use_case_id = "iot_Consumer_Filter";
        String topic = "result_" + use_case_id;

        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        EnvironmentSettings bsSettings = EnvironmentSettings.newInstance().useBlinkPlanner().inStreamingMode().build();
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(env, bsSettings);


        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerURI);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, use_case_id);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, use_case_id);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, "com.hortonworks.smm.kafka.monitoring.interceptors.MonitoringConsumerInterceptor");

        Properties propertiesProducer = new Properties();
        propertiesProducer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerURI);
        propertiesProducer.put(ProducerConfig.CLIENT_ID_CONFIG, use_case_id);
        propertiesProducer.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "com.hortonworks.smm.kafka.monitoring.interceptors.MonitoringProducerInterceptor");



        DataStream<String> iotStream = env.addSource(
                new FlinkKafkaConsumer<>("iot", new SimpleStringSchema(), properties));

        DataStream<Tuple5<Long, Integer, Integer, Integer, Integer>> stream = iotStream
                .flatMap(new trxJSONDeserializer());


        Table myTable = tableEnvironment.fromDataStream(stream);
        tableEnvironment.registerTable(dataQualityTable, myTable);

        String str = "sensor_ts, sensor_id, sensor_0, sensor_1";

        iotStream.print("input message: ");



        // execute program
        JobExecutionResult result = env.execute(use_case_id);
        JobID jobId = result.getJobID();
        System.err.println("jobId=" + jobId);


    }


    private static class trxJSONDeserializer implements FlatMapFunction<String, Tuple5<Long, Integer, Integer, Integer, Integer>> {
        private transient ObjectMapper jsonParser;

        @Override
        public void flatMap(String value, Collector<Tuple5<Long, Integer, Integer, Integer, Integer>> out) throws Exception {
            if (jsonParser == null) {
                jsonParser = new ObjectMapper();
            }
            JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);

            // get sensor_ts, sensor_id, sensor_0 AND sensor_1 from JSONObject
            Long sensor_ts = jsonNode.get("sensor_ts").asLong();
            Integer sensor_id = jsonNode.get("sensor_id").asInt();
            Integer sensor_0 = jsonNode.get("sensor_0").asInt();
            Integer sensor_1 = jsonNode.get("sensor_1").asInt();
            out.collect(new Tuple5<>(sensor_ts, sensor_id, sensor_0, sensor_1, 1));
        }

    }
}