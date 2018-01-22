package org.specnaz.impl;

import org.specnaz.utils.TestClosure;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TestsGroupNodeExecutor {
    private final TreeNode<TestsGroup> testsGroupNode;
    private final boolean runOnlyFocusedTests;

    public TestsGroupNodeExecutor(TreeNode<TestsGroup> testsGroupNode, boolean runOnlyFocusedTests) {
        this.testsGroupNode = testsGroupNode;
        this.runOnlyFocusedTests = runOnlyFocusedTests;
    }

    public Collection<ExecutableTestGroup> executableTestGroups() {
        List<ExecutableTestGroup> ret = new LinkedList<>();

        ExecutableTestGroup thisNodesExecutableTestGroup = thisNodesExecutableTestGroup();
        if (thisNodesExecutableTestGroup != null)
            ret.add(thisNodesExecutableTestGroup);

        for (TreeNode<TestsGroup> subGroupTestsNode : testsGroupNode.children()) {
            /*
             * In theory, this condition is superfluous -
             * if there are 0 tests in this tree,
             * then `testsGroupNode.value.testCases.isEmpty()`
             * will be `true` for all of the subgroups of this group,
             * and we would always return `null` from `thisNodesExecutableTestGroup`.
             * However, the JUnit notifier doesn't do well when it's called for a suite
             * Description without any non-suite Description children
             * (the results tree gets completely messed up),
             * and so we don't add them to the Description tree
             * (see the SpecnazCoreDslJUnitRunner class, method parseSubGroupDescriptions).
             * For that reason, we also need this condition here.
             */
            if (subGroupTestsNode.value.testsInTree > 0) {
                ret.addAll(new TestsGroupNodeExecutor(
                        subGroupTestsNode, runOnlyFocusedTests).executableTestGroups());
            }
        }

        return ret;
    }

    private ExecutableTestGroup thisNodesExecutableTestGroup() {
        return testsGroupNode.value.testCases.isEmpty()
                ? null
                : new ExecutableTestGroup(this);
    }

    public Collection<ExecutableTestCase> executableTestCases(Throwable beforeAllsError) {
        return testCases().stream().map(testCase -> shouldIgnoreTest(testCase)
                ? new ExecutableTestCase(testCase)
                : new ExecutableTestCase(testCase, () -> runSingleTestCase(testCase, beforeAllsError))
        ).collect(Collectors.toList());
    }

    private boolean allTestsInGroupAreIgnored() {
        return testCases().stream().allMatch(this::shouldIgnoreTest);
    }

    Collection<SingleTestCase> testCases() {
        return testsGroupNode.value.testCases;
    }

    private Throwable runSingleTestCase(SingleTestCase testCase, Throwable beforeAllsError) {
        return beforeAllsError == null
                ? shouldIgnoreTest(testCase) ? null : runSingleTestCase(testCase)
                : beforeAllsError;
    }

    private Throwable runSingleTestCase(SingleTestCase testCase) {
        Throwable e = invokeBefores();

        e = invokeTestBody(testCase, e);

        e = invokeAfters(e);

        return e;
    }

    boolean shouldIgnoreTest(SingleTestCase testCase) {
        return testCase.type == TestCaseType.IGNORED ||
                (runOnlyFocusedTests && testCase.type != TestCaseType.FOCUSED);
    }

    private Throwable invokeBefores() {
        return recursivelyInvokeFixturesAncestorsFirst(testsGroupNode, g -> g.befores);
    }

    private Throwable invokeTestBody(SingleTestCase testCase, Throwable previousError) {
        // we only run the test if none of the 'beforeEach' methods failed
        return previousError == null
                ? testCase.exercise()
                : previousError;
    }

    private Throwable invokeAfters(Throwable previousError) {
        Throwable aftersError = recursivelyInvokeFixturesAncestorsLast(testsGroupNode, g -> g.afters);
        return previousError == null ? aftersError : previousError;
    }

    public Executable beforeAllsExecutable() {
        return this::invokeBeforeAlls;
    }

    private Throwable invokeBeforeAlls() {
        return allTestsInGroupAreIgnored()
                ? null
                : recursivelyInvokeFixturesAncestorsFirst(testsGroupNode, g -> g.beforeAlls);
    }

    public Executable afterAllsExecutable() {
        return this::invokeAfterAlls;
    }

    private Throwable invokeAfterAlls() {
        return allTestsInGroupAreIgnored()
                ? null
                : recursivelyInvokeFixturesAncestorsLast(testsGroupNode, g -> g.afterAlls);
    }

    private Throwable recursivelyInvokeFixturesAncestorsFirst(
            TreeNode<TestsGroup> testsGroupNode,
            Function<TestsGroup, List<TestClosure>> extractor) {
        if (testsGroupNode == null)
            return null;

        Throwable ancestorsError = recursivelyInvokeFixturesAncestorsFirst(testsGroupNode.parent(), extractor);

        Throwable myError = invokeFixtures(extractor.apply(testsGroupNode.value));

        return ancestorsError == null ? myError : ancestorsError;
    }

    private Throwable recursivelyInvokeFixturesAncestorsLast(
            TreeNode<TestsGroup> testsGroupNode,
            Function<TestsGroup, List<TestClosure>> extractor) {
        if (testsGroupNode == null)
            return null;

        Throwable myError = invokeFixtures(extractor.apply(testsGroupNode.value));

        Throwable ancestorsError = recursivelyInvokeFixturesAncestorsLast(testsGroupNode.parent(), extractor);

        return myError == null ? ancestorsError : myError;
    }

    private Throwable invokeFixtures(List<TestClosure> fixtures) {
        Throwable ret = null;
        for (TestClosure fixture : fixtures) {
            Throwable result = SingleTestCase.invokeCallback(fixture);
            if (ret == null)
                ret = result;
        }
        return ret;
    }
}