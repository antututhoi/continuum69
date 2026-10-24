package ml.docilealligator.infinityforreddit.markdown.imageandgif;

import androidx.annotation.Nullable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import org.commonmark.node.Block;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

public class ImageAndGifBlockParser extends AbstractBlockParser {

    private final ImageAndGifBlock imageAndGifBlock;

    ImageAndGifBlockParser(MediaMetadata mediaMetadata) {
        this.imageAndGifBlock = new ImageAndGifBlock(mediaMetadata);
    }

    @Override
    public Block getBlock() {
        return imageAndGifBlock;
    }

    @Override
    @Nullable
    public BlockContinue tryContinue(ParserState parserState) {
        return null;
    }

    public static class Factory extends AbstractBlockParserFactory {
        private final Pattern redditPreviewPattern =  Pattern.compile("!\\[.*]\\(https://preview.redd.it/(\\w+).(jpg|png|jpeg)((\\?+[-a-zA-Z0-9()@:%_+.~#?&/=]*)|)\\)");;
        private final Pattern iRedditPattern = Pattern.compile("!\\[.*]\\(https://i.redd.it/(\\w+).(jpg|png|jpeg|gif)\\)");
        private final Pattern gifPattern = Pattern.compile("!\\[gif]\\((giphy\\|\\w+(\\|downsized)?)\\)");
        /** 69 mod: a line that is only a link to a picture (i.redd.it, preview.redd.it, i.imgur.com) is shown as the picture. */
        private final Pattern mod69LinkLine = Pattern.compile(
                "^\\s*(?:!?\\[[^\\]]*]\\()?<?(https?://(?:i\\.redd\\.it|preview\\.redd\\.it|i\\.imgur\\.com)/[^\\s()<>]+?\\.(?:jpe?g|png|gif|gifv|webp)(?:\\?[^\\s()<>]*)?)>?\\)?\\s*$",
                Pattern.CASE_INSENSITIVE);
        @Nullable
        private Map<String, MediaMetadata> mediaMetadataMap;

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            String line = state.getLine().toString();
            if (mediaMetadataMap == null) {
                return mod69TryLink(line);
            }
            Matcher matcher = redditPreviewPattern.matcher(line);
            if (matcher.find() && matchedToEndOfLine(matcher, line)) {
                String id = matcher.group(1);
                if (id != null) {
                    return mediaMetadataMap.containsKey(id) ? BlockStart.of(new ImageAndGifBlockParser(mediaMetadataMap.get(id))) : BlockStart.none();
                }
            }

            matcher = iRedditPattern.matcher(line);
            if (matcher.find() && matchedToEndOfLine(matcher, line)) {
                String id = matcher.group(1);
                if (id != null) {
                    return mediaMetadataMap.containsKey(id) ? BlockStart.of(new ImageAndGifBlockParser(mediaMetadataMap.get(id))) : BlockStart.none();
                }
            }

            matcher = gifPattern.matcher(line);
            if (matcher.find() && matchedToEndOfLine(matcher, line)) {
                String id = matcher.group(1);
                return mediaMetadataMap.containsKey(id) ? BlockStart.of(new ImageAndGifBlockParser(mediaMetadataMap.get(id))) : BlockStart.none();
            }
            return mod69TryLink(line);
        }

        private BlockStart mod69TryLink(String line) {
            Matcher matcher = mod69LinkLine.matcher(line);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url != null) {
                    String id = url;
                    int slash = url.lastIndexOf('/');
                    int dot = url.indexOf('.', slash);
                    if (slash >= 0 && dot > slash) id = url.substring(slash + 1, dot);
                    Map<String, MediaMetadata> map = mediaMetadataMap;
                    if (map != null && map.containsKey(id)) {
                        return BlockStart.of(new ImageAndGifBlockParser(map.get(id)));
                    }
                    return BlockStart.of(new ImageAndGifBlockParser(
                            ml.docilealligator.infinityforreddit.mod69.Mod69Comments.fromLink(url)));
                }
            }
            return BlockStart.none();
        }

        /**
         * Whether {@code matcher} ran to the end of {@code line}, disregarding trailing whitespace.
         *
         * Reddit's editor ends an embedded image or gif with two spaces -- a markdown hard break --
         * whenever the author typed anything after it. Demanding the match end at the last character
         * of the line missed every one of those, leaving the media to the inline emote renderer,
         * which sizes it as a text glyph and animates gifs regardless of the comment-gif autoplay
         * setting.
         */
        private static boolean matchedToEndOfLine(Matcher matcher, String line) {
            for (int i = matcher.end(); i < line.length(); i++) {
                if (!Character.isWhitespace(line.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        public void setMediaMetadataMap(@Nullable Map<String, MediaMetadata> mediaMetadataMap) {
            this.mediaMetadataMap = mediaMetadataMap;
        }
    }
}
