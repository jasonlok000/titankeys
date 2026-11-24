import { Row, Screen, Section, openURL } from "ink";
import { arrowOutward, close } from "ink/icons";
import { notesSince, releaseNotesURL } from "./release-notes";
import { Note } from "./note";
import { Topic } from "./topic";

/**
 * The release notes of every version newer than `from`, the version someone had before this update, newest first, so a
 * skipped version's changes still show (#121); with no `from`, the current version's. With `onClose` it opens by itself
 * after an update; otherwise it has a back arrow.
 */
export function WhatsNew({ version, from, onClose }: { version: string; from?: string | null; onClose?: () => void }) {
  const versions = notesSince(from);
  const several = versions.length > 1;
  const subtitle = several ? `New since ${from}. You're on TitanKeys ${version}.`
    : onClose ? `You're on TitanKeys ${version}.` : `Version ${version}`;
  return <Screen title="What's new" subtitle={subtitle} rightAction={onClose && { icon: close, onPress: onClose }}>
    {versions.flatMap(notes => notes.sections.map(section => {
      const title = several && !section.title.includes(notes.version) ? `${notes.version}: ${section.title}` : section.title;
      return <Section key={`${notes.version} ${section.title}`} title={title}>
        {section.notes.map(note => note.detail
          ? <Topic key={note.title} title={note.title}>{note.detail}</Topic>
          : <Note key={note.title}>{`• ${note.title}`}</Note>)}
      </Section>;
    }))}
    <Row title="Full release notes" endIcon={arrowOutward} onPress={() => void openURL(releaseNotesURL(version))} />
  </Screen>;
}
