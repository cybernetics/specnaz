package org.specnaz.impl;

import org.specnaz.TestSettings;
import org.specnaz.params.ParamsExpected1;
import org.specnaz.params.ParamsExpectedException1;
import org.specnaz.params.TestClosureParams1;
import org.specnaz.params.impl.AbstractParametrizedTest1;
import org.specnaz.params.impl.ParametrizedPositiveTest1;
import org.specnaz.params.impl.ParametrizedExceptionTest1;
import org.specnaz.utils.TestClosure;
import org.specnaz.utils.ThrowableExpectations;

import java.util.LinkedList;
import java.util.List;

public final class TestsGroupNodeAccumulator {
    private final String description;
    private final TestCaseType testCaseType;
    private final List<TestClosure> beforeAlls = new LinkedList<>(),
                                    befores    = new LinkedList<>(),
                                    afters     = new LinkedList<>(),
                                    afterAlls  = new LinkedList<>();
    private final List<SingleTestCase> testCases  = new LinkedList<>();
    private final List<AbstractParametrizedTest1<?>> parametrizedTests = new LinkedList<>();
    private final List<TreeNode<TestsGroup>> subgroups = new LinkedList<>();
    private boolean containsFocusedTests = false;

    public TestsGroupNodeAccumulator(String description, TestCaseType testCaseType) {
        this.description = description;
        this.testCaseType = testCaseType;
        if (testCaseType == TestCaseType.FOCUSED)
            containsFocusedTests = true;
    }

    public void addBeforeAll(TestClosure closure) {
        beforeAlls.add(closure);
    }

    public void addBeforeEach(TestClosure closure) {
        befores.add(closure);
    }

    public TestSettings addPositiveTest(String description, TestClosure testBody, TestCaseType testCaseType) {
        if (testCaseType == TestCaseType.FOCUSED)
            containsFocusedTests = true;

        TestSettings testSettings = new TestSettings();
        testCases.add(new SinglePositiveTestCase(testSettings,
                description, testBody, descendantTestType(testCaseType)));
        return testSettings;
    }

    public <T extends Throwable> ThrowableExpectations<T> addExceptionTest(Class<T> expectedException,
            String description, TestClosure testBody, TestCaseType testCaseType) {
        if (testCaseType == TestCaseType.FOCUSED)
            containsFocusedTests = true;

        ThrowableExpectations<T> throwableExpectations = new ThrowableExpectations<>(expectedException);
        testCases.add(new SingleExceptionTestCase<>(throwableExpectations,
                description, testBody, descendantTestType(testCaseType)));
        return throwableExpectations;
    }

    public void addAfterEach(TestClosure closure) {
        afters.add(closure);
    }

    public void addAfterAll(TestClosure closure) {
        afterAlls.add(closure);
    }

    public TestsGroupNodeAccumulator subgroupAccumulator(String description, TestCaseType testCaseType) {
        return new TestsGroupNodeAccumulator(description, descendantTestType(testCaseType));
    }

    public void addSubgroup(TreeNode<TestsGroup> subgroupNode) {
        subgroups.add(subgroupNode);
    }

    public <P> ParamsExpected1<P> addParametrizedPositiveTest1(String description, TestClosureParams1<P> testBody,
            TestCaseType testCaseType) {
        if (testCaseType == TestCaseType.FOCUSED)
            containsFocusedTests = true;

        TestSettings testSettings = new TestSettings();
        ParametrizedPositiveTest1<P> parametrizedTest = new ParametrizedPositiveTest1<>(testSettings,
                description, testBody, descendantTestType(testCaseType));
        parametrizedTests.add(parametrizedTest);
        return new ParamsExpected1<>(parametrizedTest, testSettings);
    }

    public <T extends Throwable, P> ParamsExpectedException1<T, P> addParametrizedExceptionTest1(
            Class<T> expectedException, String description, TestClosureParams1<P> testBody, TestCaseType testCaseType) {
        if (testCaseType == TestCaseType.FOCUSED)
            containsFocusedTests = true;

        ThrowableExpectations<T> throwableExpectations = new ThrowableExpectations<>(expectedException);
        ParametrizedExceptionTest1<T, P> parametrizedTest = new ParametrizedExceptionTest1<>(throwableExpectations,
                description, testBody, descendantTestType(testCaseType));
        parametrizedTests.add(parametrizedTest);
        return new ParamsExpectedException1<>(parametrizedTest, throwableExpectations);
    }

    public TreeNode<TestsGroup> build() {
        // count tests in subgroups
        int testsInSubgroups = subgroups.stream().mapToInt(node -> node.value.testsInTree).sum();
        // see if any subgroup contains focused tests
        containsFocusedTests |= subgroups.stream().anyMatch(s -> s.value.containsFocusedTests);

        List<SingleTestCase> testCases = new LinkedList<>(this.testCases);
        for (AbstractParametrizedTest1<?> parametrizedTest : parametrizedTests) {
            testCases.addAll(parametrizedTest.testCases());
        }

        TestsGroup testsGroup = new TestsGroup(description, beforeAlls, befores,
                testCases, afters, afterAlls, testsInSubgroups, containsFocusedTests);
        TreeNode<TestsGroup> testsGroupTreeNode = new TreeNode<>(testsGroup);
        for (TreeNode<TestsGroup> subgroupNode: subgroups) {
            testsGroupTreeNode.attach(subgroupNode);
            incrementFixturesCount(subgroupNode);
        }
        return testsGroupTreeNode;
    }

    private void incrementFixturesCount(TreeNode<TestsGroup> testsGroupNode) {
        TestsGroup testsGroup = testsGroupNode.value;
        testsGroup.incrementBeforeAllsCount(beforeAlls.size());
        testsGroup.incrementAfterAllsCount(afterAlls.size());

        for (TreeNode<TestsGroup> child : testsGroupNode.children()) {
            incrementFixturesCount(child);
        }
    }

    private TestCaseType descendantTestType(TestCaseType testCaseType) {
        return this.testCaseType.descendantTestType(testCaseType);
    }
}