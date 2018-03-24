/**
 * FILE: PointKnnTest.java
 * PATH: org.datasyslab.geospark.spatialOperator.PointKnnTest.java
 * Copyright (c) 2015-2017 GeoSpark Development Team
 * All rights reserved.
 */
package org.datasyslab.geospark.spatialOperator;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.datasyslab.geospark.enums.FileDataSplitter;
import org.datasyslab.geospark.enums.IndexType;
import org.datasyslab.geospark.knnJudgement.GeometryDistanceComparator;
import org.datasyslab.geospark.spatialRDD.PointRDD;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

// TODO: Auto-generated Javadoc

/**
 * The Class PointKnnTest.
 */
public class PointKnnTest
{

    /**
     * The sc.
     */
    public static JavaSparkContext sc;

    /**
     * The prop.
     */
    static Properties prop;

    /**
     * The input.
     */
    static InputStream input;

    /**
     * The Input location.
     */
    static String InputLocation;

    /**
     * The offset.
     */
    static Integer offset;

    /**
     * The splitter.
     */
    static FileDataSplitter splitter;

    /**
     * The index type.
     */
    static IndexType indexType;

    /**
     * The num partitions.
     */
    static Integer numPartitions;

    /**
     * The loop times.
     */
    static int loopTimes;

    /**
     * The query point.
     */
    static Point queryPoint;

    /**
     * The top K.
     */
    static int topK;

    /**
     * Once executed before all.
     */
    @BeforeClass
    public static void onceExecutedBeforeAll()
    {
        SparkConf conf = new SparkConf().setAppName("PointKnn").setMaster("local[4]");
        sc = new JavaSparkContext(conf);
        Logger.getLogger("org").setLevel(Level.WARN);
        Logger.getLogger("akka").setLevel(Level.WARN);
        prop = new Properties();
        input = PointKnnTest.class.getClassLoader().getResourceAsStream("point.test.properties");

        //Hard code to a file in resource folder. But you can replace it later in the try-catch field in your hdfs system.
        InputLocation = "file://" + PointKnnTest.class.getClassLoader().getResource("primaryroads.csv").getPath();

        offset = 0;
        splitter = null;
        indexType = null;
        numPartitions = 0;
        GeometryFactory fact = new GeometryFactory();
        try {
            // load a properties file
            prop.load(input);
            // There is a field in the property file, you can edit your own file location there.
            // InputLocation = prop.getProperty("inputLocation");
            InputLocation = "file://" + PointKnnTest.class.getClassLoader().getResource(prop.getProperty("inputLocation")).getPath();
            offset = Integer.parseInt(prop.getProperty("offset"));
            splitter = FileDataSplitter.getFileDataSplitter(prop.getProperty("splitter"));
            indexType = IndexType.getIndexType(prop.getProperty("indexType"));
            numPartitions = Integer.parseInt(prop.getProperty("numPartitions"));
            loopTimes = 5;
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        queryPoint = fact.createPoint(new Coordinate(-84.01, 34.01));
        topK = 100;
    }

    /**
     * Teardown.
     */
    @AfterClass
    public static void teardown()
    {
        sc.stop();
    }

    /**
     * Test spatial knn query.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSpatialKnnQuery()
            throws Exception
    {
        PointRDD pointRDD = new PointRDD(sc, InputLocation, offset, splitter, true);

        for (int i = 0; i < loopTimes; i++) {
            List<Point> result = KNNQuery.SpatialKnnQuery(pointRDD, queryPoint, topK, false);
            assert result.size() > -1;
            assert result.get(0).getUserData().toString() != null;
            //System.out.println(result.get(0).getUserData().toString());
        }
    }

    /**
     * Test spatial knn query using index.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSpatialKnnQueryUsingIndex()
            throws Exception
    {
        PointRDD pointRDD = new PointRDD(sc, InputLocation, offset, splitter, true);
        pointRDD.buildIndex(IndexType.RTREE, false);
        for (int i = 0; i < loopTimes; i++) {
            List<Point> result = KNNQuery.SpatialKnnQuery(pointRDD, queryPoint, topK, true);
            assert result.size() > -1;
            assert result.get(0).getUserData().toString() != null;
            //System.out.println(result.get(0).getUserData().toString());
        }
    }

    /**
     * Test spatial KNN correctness.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSpatialKNNCorrectness()
            throws Exception
    {
        PointRDD pointRDD = new PointRDD(sc, InputLocation, offset, splitter, true);
        List<Point> resultNoIndex = KNNQuery.SpatialKnnQuery(pointRDD, queryPoint, topK, false);
        pointRDD.buildIndex(IndexType.RTREE, false);
        List<Point> resultWithIndex = KNNQuery.SpatialKnnQuery(pointRDD, queryPoint, topK, true);
        GeometryDistanceComparator geometryDistanceComparator = new GeometryDistanceComparator(this.queryPoint, true);
        List<Point> mResultNoIndex = new ArrayList<>(resultNoIndex);
        List<Point> mResultWithIndex = new ArrayList<>(resultNoIndex);
        Collections.sort(mResultNoIndex, geometryDistanceComparator);
        Collections.sort(mResultWithIndex, geometryDistanceComparator);
        int difference = 0;
        for (int i = 0; i < topK; i++) {
            if (geometryDistanceComparator.compare(resultNoIndex.get(i), resultWithIndex.get(i)) != 0) {
                difference++;
            }
        }
        assert difference == 0;
    }
}