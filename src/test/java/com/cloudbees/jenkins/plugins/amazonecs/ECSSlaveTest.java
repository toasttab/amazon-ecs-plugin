package com.cloudbees.jenkins.plugins.amazonecs;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import com.amazonaws.services.ecs.model.ClientException;

import com.amazonaws.services.ecs.model.Task;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;

import static org.mockito.Mockito.*;

public class ECSSlaveTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private ECSTaskTemplate getTaskTemplate() {
        return new ECSTaskTemplate(
                "templateName",
                "label",
                "agentContainerName",
                "taskDefinitionOverride",
                null,
                "image",
                "repositoryCredentials",
                "launchType",
                "operatingSystemFamily",
                "cpuArchitecture",
                false,
                null,
                "networkMode",
                "remoteFSRoot",
                false,
                null,
                0,
                0,
                0,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false, false);
    }

    @Test
    public void terminateRunningTask() throws Exception {

        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        TaskListener listener = mock(TaskListener.class);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setTaskArn("mytaskarn");
        sut.setClusterArn("myclusterarn");
        sut.setNodeName("mynode");

        sut._terminate(listener);

        // Delete the task
        verify(ecsService, times(1)).stopTask("mytaskarn", "myclusterarn");
    }

    @Test
    public void terminate_ThrowsException_ignoreException() throws Exception {

        ECSService ecsService = mock(ECSService.class);
        Mockito.doThrow(new ClientException("failed"))
                .when(ecsService).stopTask("mytaskarn", "myclusterarn");

        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        TaskListener listener = mock(TaskListener.class);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setTaskArn("mytaskarn");
        sut.setClusterArn("myclusterarn");
        sut.setNodeName("mynode");

        sut._terminate(listener);

        // Delete the task
        verify(ecsService, times(1)).stopTask("mytaskarn", "myclusterarn");
    }

    private void test_node_is_survivable_with_last_status_and_desired_status(String lastStatus, String desiredStatus, boolean expectedSurvivable) throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setClusterArn("myclusterarn");
        sut.setTaskArn("mytaskarn");

        Task task = mock(Task.class);
        Mockito.when(task.getLastStatus()).thenReturn(lastStatus);
        Mockito.when(task.getDesiredStatus()).thenReturn(desiredStatus);
        Mockito.when(ecsService.describeTask(sut.getTaskArn(), sut.getClusterArn())).thenReturn(task);

        if (expectedSurvivable) {
            Assert.assertTrue(sut.isSurvivable());
        } else {
            Assert.assertFalse(sut.isSurvivable());
        }
    }

    @Test
    public void node_is_survivable_if_task_is_provisioning_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("PROVISIONING", "RUNNING", true);
    }

    @Test
    public void node_is_survivable_if_task_is_running_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("RUNNING", "RUNNING", true);
    }

    @Test
    public void node_is_not_survivable_if_task_is_stopped_and_desired_status_is_running() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("STOPPED", "RUNNING", false);
    }

    @Test
    public void node_is_not_survivable_if_task_is_running_and_desired_status_is_stopped() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("RUNNING", "STOPPED", false);
    }

    @Test
    public void node_is_not_survivable_if_task_is_stopped_and_desired_status_is_stopped() throws Exception {
        test_node_is_survivable_with_last_status_and_desired_status("STOPPED", "STOPPED", false);
    }

    @Test
    public void node_is_not_survivable_if_task_cannot_be_found() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());
        sut.setClusterArn("myclusterarn");
        sut.setTaskArn("mytaskarn");

        Mockito.when(ecsService.describeTask(sut.getTaskArn(), sut.getClusterArn())).thenReturn(null);

        Assert.assertFalse(sut.isSurvivable());
    }

    @Test
    public void agent_has_1_executor_as_default() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());

        Assert.assertEquals(1, sut.getNumExecutors());
    }

    @Test
    public void agent_has_4_executors_when_configured() throws Exception {
        ECSService ecsService = mock(ECSService.class);
        ECSCloud cloud = mock(ECSCloud.class);
        Mockito.when(cloud.getEcsService()).thenReturn(ecsService);
        ECSTaskTemplate template = getTaskTemplate();

        when(cloud.getNumExecutors()).thenReturn(4);

        ECSSlave sut = new ECSSlave(cloud, "myagent", template, new JNLPLauncher());

        Assert.assertEquals(4, sut.getNumExecutors());
    }
}
