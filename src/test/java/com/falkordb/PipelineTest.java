package com.falkordb;

import java.util.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;

public class PipelineTest {

    private GraphContextGenerator api;

    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");

    }

    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    @Test
    public void testSync() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.incr("x");
            pipeline.get("x");
            pipeline.query("MATCH (n:Person{name:'a'}) RETURN n");
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            // Redis set command
            Assert.assertEquals(String.class, results.get(0).getClass());
            Assert.assertEquals("OK", results.get(0));

            // Redis graph command
            Assert.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet resultSet = (ResultSet) results.get(1);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            Assert.assertEquals(ResultSetImpl.class, results.get(2).getClass());
            resultSet = (ResultSet) results.get(2);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            // Redis incr command
            Assert.assertEquals(Long.class, results.get(3).getClass());
            Assert.assertEquals(2L, results.get(3));

            // Redis get command
            Assert.assertEquals(String.class, results.get(4).getClass());
            Assert.assertEquals("2", results.get(4));

            // Graph query result
            Assert.assertEquals(ResultSetImpl.class, results.get(5).getClass());
            resultSet = (ResultSet) results.get(5);

            Assert.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();

            List<String> schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("n", schemaNames.get(0));

            Property<String> nameProperty = new Property<>("name", "a");

            Node expectedNode = new Node();
            expectedNode.setId(0);
            expectedNode.addLabel("Person");
            expectedNode.addProperty(nameProperty);
            // see that the result were pulled from the right graph
            Assert.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("n"), record.keys());
            Assert.assertEquals(expectedNode, record.getValue("n"));

            Assert.assertEquals(ResultSetImpl.class, results.get(6).getClass());
            resultSet = (ResultSet) results.get(6);

            Assert.assertNotNull(resultSet.getHeader());
            header = resultSet.getHeader();

            schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("label", schemaNames.get(0));

            Assert.assertEquals(1, resultSet.size());

            iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("label"), record.keys());
            Assert.assertEquals("Person", record.getValue("label"));
        }
    }

    @Test
    public void testReadOnlyQueries() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();

            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.readOnlyQuery("MATCH (n:Person{name:'a'}) RETURN n");
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            verifyReadOnlyQueryResults(results);
        }
    }

    @Test
    public void testReadOnlyQueriesWithParameters() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();

            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            Map<String, Object> params = new HashMap<>();
            params.put("name", "a");
            pipeline.readOnlyQuery("MATCH (n:Person{name:$name}) RETURN n", params);
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            verifyReadOnlyQueryResults(results);
        }
    }

    protected void verifyReadOnlyQueryResults(List<Object> results) {
        // Redis set command
        Assert.assertEquals(String.class, results.get(0).getClass());
        Assert.assertEquals("OK", results.get(0));

        // Redis graph command
        Assert.assertEquals(ResultSetImpl.class, results.get(1).getClass());
        ResultSet resultSet = (ResultSet) results.get(1);
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
        Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

        Assert.assertEquals(ResultSetImpl.class, results.get(2).getClass());
        resultSet = (ResultSet) results.get(2);
        Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
        Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

        // Graph read-only query result
        Assert.assertEquals(ResultSetImpl.class, results.get(3).getClass());
        resultSet = (ResultSet) results.get(3);

        Assert.assertNotNull(resultSet.getHeader());
        Header header = resultSet.getHeader();

        List<String> schemaNames = header.getSchemaNames();
        Assert.assertNotNull(schemaNames);
        Assert.assertEquals(1, schemaNames.size());
        Assert.assertEquals("n", schemaNames.get(0));

        Property<String> nameProperty = new Property<>("name", "a");

        Node expectedNode = new Node();
        expectedNode.setId(0);
        expectedNode.addLabel("Person");
        expectedNode.addProperty(nameProperty);
        // see that the result were pulled from the right graph
        Assert.assertEquals(1, resultSet.size());

        Iterator<Record> iterator = resultSet.iterator();
        Assert.assertTrue(iterator.hasNext());
        Record record = iterator.next();
        Assert.assertFalse(iterator.hasNext());
        Assert.assertEquals(Arrays.asList("n"), record.keys());
        Assert.assertEquals(expectedNode, record.getValue("n"));

        Assert.assertEquals(ResultSetImpl.class, results.get(4).getClass());
        resultSet = (ResultSet) results.get(4);

        Assert.assertNotNull(resultSet.getHeader());
        header = resultSet.getHeader();

        schemaNames = header.getSchemaNames();
        Assert.assertNotNull(schemaNames);
        Assert.assertEquals(1, schemaNames.size());
        Assert.assertEquals("label", schemaNames.get(0));

        Assert.assertEquals(1, resultSet.size());

        iterator = resultSet.iterator();
        Assert.assertTrue(iterator.hasNext());
        record = iterator.next();
        Assert.assertFalse(iterator.hasNext());
        Assert.assertEquals(Arrays.asList("label"), record.keys());
        Assert.assertEquals("Person", record.getValue("label"));
    }

    @Test
    public void testGraphCopy() {
        Iterator<Record> originalResultSetIterator;
        try (GraphContext c = api.getContext()) {
            // Create sample data and copy the graph
            GraphPipeline pipeline = c.pipelined();
            pipeline.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
            pipeline.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            pipeline.copyGraph("social-copied");
            List<Object> results = pipeline.syncAndReturnAll();

            ResultSet originalResultSet = (ResultSet) results.get(1);
            originalResultSetIterator = originalResultSet.iterator();
        }

        GraphContextGenerator api2 = FalkorDB.driver().graph("social-copied");
        try {
            // Compare graph contents
            ResultSet copiedResultSet = api2.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            Iterator<Record> copiedResultSetIterator = copiedResultSet.iterator();
            while (originalResultSetIterator.hasNext()) {
                Assert.assertTrue(copiedResultSetIterator.hasNext());
                Assert.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            api2.deleteGraph();
            api2.close();
        }
    }
}
