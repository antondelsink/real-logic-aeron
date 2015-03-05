package co.uk.real_logic.aeron.tools;

import org.apache.commons.cli.ParseException;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import uk.co.real_logic.aeron.tools.ChannelDescriptor;
import uk.co.real_logic.aeron.tools.PubSubOptions;

/**
 * Created by bhorst on 3/3/15.
 */
public class PubSubOptionsTest
{
    PubSubOptions opts;
    @Before
    public void setUp()
    {
        opts = new PubSubOptions();
    }

    @Test
    public void threadsShorthandValid() throws Exception
    {
        String[] args = { "-t", "1234" };
        opts.parseArgs(args);
        assertThat(opts.getThreads(), is(1234L));
    }

    @Test
    public void threadsLonghandValid() throws Exception
    {
        String[] args = { "--threads", "1234" };
        opts.parseArgs(args);
        assertThat(opts.getThreads(), is(1234L));
    }

    @Test (expected=ParseException.class)
    public void threadsInvalid() throws Exception
    {
        String[] args = { "-t", "asdf" };
        opts.parseArgs(args);
    }

    @Test (expected=ParseException.class)
    public void threadsLonghandInvalid() throws Exception
    {
        String[] args = { "--threads", "asdf" };
        opts.parseArgs(args);
    }

    @Test
    public void channel() throws Exception
    {
        String[] args = { "--channels", "udp://127.0.0.1:12345" };
        opts.parseArgs(args);
        assertThat("FAIL: Exactly one channel.",
                opts.getChannels().size(), is(1));

        ChannelDescriptor cd = opts.getChannels().get(0);
        int[] streamIds = cd.getStreamIdentifiers();

        assertThat("FAIL: Exactly one stream identifier on the channel.",
                streamIds.length, is(1));
        assertThat("FAIL: Channel is udp://127.0.0.1:12345",
                cd.getChannel(), is("udp://127.0.0.1:12345"));
        assertThat("FAIL: Stream ID is 1",
                cd.getStreamIdentifiers()[0], is(1));
    }

    @Test
    public void channelWithStreamId() throws Exception
    {
        String[] args = { "--channels", "udp://127.0.0.1:12345#100" };
        opts.parseArgs(args);

        assertThat("FAIL: Exactly one channel.",
                opts.getChannels().size(), is(1));

        ChannelDescriptor cd = opts.getChannels().get(0);
        int[] streamIds = cd.getStreamIdentifiers();

        assertThat("FAIL: Exactly one stream identifier on the channel.",
                streamIds.length, is(1));
        assertThat("FAIL: Channel is udp://127.0.0.1:12345",
                cd.getChannel(), is("udp://127.0.0.1:12345"));
        assertThat("FAIL: Stream ID is 100",
                cd.getStreamIdentifiers()[0], is(100));
    }

    @Test
    public void channelWithPortRange() throws Exception
    {
        String[] args = { "--channels", "udp://127.0.0.1:12345-12347" };
        opts.parseArgs(args);

        assertThat("FAIL: Expected 3 channels.",
                opts.getChannels().size(), is(3));
        assertThat("FAIL: Channel 1 incorrect.",
                opts.getChannels().get(0).getChannel(), is("udp://127.0.0.1:12345"));
        assertThat("FAIL: Channel 2 incorrect",
                opts.getChannels().get(1).getChannel(), is("udp://127.0.0.1:12346"));
        assertThat("FAIL: Channel 3 incorrect",
                opts.getChannels().get(2).getChannel(), is("udp://127.0.0.1:12347"));
    }

    @Test
    public void channelWithStreamIdRange() throws Exception
    {
        String[] args = { "--channels", "udp://127.0.0.1:12345#100-102" };
        opts.parseArgs(args);

        assertThat("FAIL: Expected 1 channel.",
                opts.getChannels().size(), is(1));
        ChannelDescriptor cd = opts.getChannels().get(0);

        assertThat("FAIL: Expected 3 stream IDs on channel.",
                cd.getStreamIdentifiers().length, is(3));
        assertThat("FAIL: stream-id 1 is wrong value.",
                cd.getStreamIdentifiers()[0], is(100));
        assertThat("FAIL: Stream-id 2 is wrong value.",
                cd.getStreamIdentifiers()[1], is(101));
        assertThat("FAIL: Stream-id 3 is wrong value.",
                cd.getStreamIdentifiers()[2], is(102));
    }

    /**
     * Test that channels can be comma separated values with port and stream id ranges.
     * We should end up with 6 total channels, each with 2 stream-ids
     */
    @Test
    public void channelCsvWithPortAndStreamIdRange() throws  Exception
    {
        ChannelDescriptor cd;
        String[] args = { "--channels",
                "udp://127.0.0.1:5000-5001#1-2,udp://224.9.10.11:6000-6001#600-601,udp://192.168.0.1:7000-7001#700-701"};
        opts.parseArgs(args);

        assertThat("FAIL: Expected 6 channels",
                opts.getChannels().size(), is(6));

        cd = opts.getChannels().get(0);
        assertThat("FAIL: Wrong address for channel 1",
                cd.getChannel(), is("udp://127.0.0.1:5000"));
        assertThat("FAIL: Wrong number of stream IDs on channel 1",
                cd.getStreamIdentifiers().length, is(2));

        cd = opts.getChannels().get(1);
        assertThat("FAIL: Wrong address for channel 2",
                cd.getChannel(), is("udp://127.0.0.1:5001"));
        assertThat("FAIL: Wrong number of stream IDs on channel 2",
                cd.getStreamIdentifiers().length, is(2));

        cd = opts.getChannels().get(2);
        assertThat("FAIL: Wrong address for channel 3",
                cd.getChannel(), is("udp://224.9.10.11:6000"));
        assertThat("FAIL: Wrong number of stream IDs on channel 3",
                cd.getStreamIdentifiers().length, is(2));

        cd = opts.getChannels().get(3);
        assertThat("FAIL: Wrong address for channel 4",
                cd.getChannel(), is("udp://224.9.10.11:6001"));
        assertThat("FAIL: Wrong number of stream IDs on channel 4",
                cd.getStreamIdentifiers().length, is(2));

        cd = opts.getChannels().get(4);
        assertThat("FAIL: Wrong address for channel 5",
                cd.getChannel(), is("udp://192.168.0.1:7000"));
        assertThat("FAIL: Wrong number of stream IDs on channel 5",
                cd.getStreamIdentifiers().length, is(2));

        cd = opts.getChannels().get(5);
        assertThat("FAIL: Wrong address for channel 6",
                cd.getChannel(), is("udp://192.168.0.1:7001"));
        assertThat("FAIL: Wrong number of stream IDs on channel 6",
                cd.getStreamIdentifiers().length, is(2));
    }
}
