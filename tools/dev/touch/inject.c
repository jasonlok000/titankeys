// tk-inject: plays a script of input events into the phone's own touchpad and keyboard, with real timing.
//
// It writes to the real /dev/input/event* where it may. Where SELinux forbids that, as on the Titan 2 Elite's Android 16, it
// makes a twin of the device through /dev/uinput, which adb's shell may use (see open_twin), and Android reads the twin's events
// as the hardware's own. `sendevent` starts one process per event, far too slow for a swipe; this plays a whole gesture from one
// file.
//
// Usage: tk-inject [--dry-run] SCRIPT
// The script, one line each, '#' for comments:
//   dev N NAME             device N is the input device called NAME: only "touchPad" and "TitanKey" are allowed
//   MS N TYPE CODE VALUE   at MS milliseconds from the start (a decimal), write that event to device N
// Times never go back. A script is at most 60 s and 200,000 events.
//
// Whatever happens (the end, an error, Ctrl+C, adb going away), every key still down is let go and a touch still down is lifted,
// so nothing is left held on the phone. --dry-run reads and checks the script and prints it, without opening any device.
#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#define MAX_DEVICES 4
#define MAX_EVENTS 200000
#define MAX_MILLIS 60000.0
#define MAX_KEYS 768

static const char *ALLOWED[] = {"touchPad", "TitanKey"};

typedef struct { double ms; int dev; int type; int code; int value; } Event;

static int fds[MAX_DEVICES] = {-1, -1, -1, -1};
static char names[MAX_DEVICES][64];
static unsigned char down[MAX_DEVICES][MAX_KEYS];
static volatile sig_atomic_t stop = 0;

static void on_signal(int sig) { (void)sig; stop = 1; }

static int write_event(int dev, int type, int code, int value) {
  struct input_event e;
  memset(&e, 0, sizeof e);
  e.type = (unsigned short)type;
  e.code = (unsigned short)code;
  e.value = value;
  if (write(fds[dev], &e, sizeof e) != (ssize_t)sizeof e) return -1;
  if (type == EV_KEY && code >= 0 && code < MAX_KEYS) down[dev][code] = value != 0;
  return 0;
}

// Lets go of every key still down and lifts a touch still down.
static void release_all(void) {
  for (int d = 0; d < MAX_DEVICES; d++) {
    if (fds[d] < 0) continue;
    int any = 0;
    for (int k = 0; k < MAX_KEYS; k++) {
      if (!down[d][k]) continue;
      write_event(d, EV_KEY, k, 0);
      if (k == BTN_TOUCH) write_event(d, EV_SYN, SYN_MT_REPORT, 0);
      any = 1;
    }
    if (any) write_event(d, EV_SYN, SYN_REPORT, 0);
  }
}

// Keys that would leave the app or act on the phone itself: the keyboard has them, and a script never sends them.
static int forbidden(int code) {
  return code == KEY_POWER || code == KEY_HOMEPAGE || code == KEY_HOME || code == KEY_BACK || code == KEY_APPSELECT
      || code == KEY_VOLUMEUP || code == KEY_VOLUMEDOWN || code == KEY_MUTE || code == KEY_SLEEP || code == KEY_WAKEUP;
}

static int allowed(const char *name) {
  for (size_t i = 0; i < sizeof ALLOWED / sizeof *ALLOWED; i++) if (strcmp(name, ALLOWED[i]) == 0) return 1;
  return 0;
}

// Opens the /dev/input/event* whose name is NAME, with FLAGS. -1 if none is, or it can't be opened so.
static int open_named(const char *name, int flags) {
  DIR *dir = opendir("/dev/input");
  if (!dir) return -1;
  struct dirent *entry;
  int found = -1;
  while ((entry = readdir(dir)) && found < 0) {
    if (strncmp(entry->d_name, "event", 5) != 0) continue;
    char path[300], got[128] = {0};
    snprintf(path, sizeof path, "/dev/input/%s", entry->d_name);
    int fd = open(path, flags | O_CLOEXEC);
    if (fd < 0) continue;
    if (ioctl(fd, EVIOCGNAME(sizeof got - 1), got) >= 0 && strcmp(got, name) == 0) found = fd;
    else close(fd);
  }
  closedir(dir);
  return found;
}

#define BITS_LONG (8 * sizeof(long))
#define TEST_BIT(bits, n) (((bits)[(n) / BITS_LONG] >> ((n) % BITS_LONG)) & 1)

// Where SELinux keeps the shell from writing the real device (Android 16 on the Titan 2 Elite does), a twin of it through
// /dev/uinput: the same name, bus, vendor, product and version, the same keys, axes and ranges, all read from the real device,
// which the shell may read. Android then gives the twin the real one's configuration (touchPad.idc, TitanKey.kl and .kcm),
// and TitanKeys knows the touchpad by its name. The kernel removes the twin when its file closes, even if this is killed.
static int open_twin(const char *name) {
  int real = open_named(name, O_RDONLY);
  if (real < 0) return -1;
  struct input_id id;
  unsigned long types[EV_MAX / BITS_LONG + 1] = {0}, keys[KEY_MAX / BITS_LONG + 1] = {0};
  unsigned long axes[ABS_MAX / BITS_LONG + 1] = {0}, props[INPUT_PROP_MAX / BITS_LONG + 1] = {0};
  if (ioctl(real, EVIOCGID, &id) < 0 || ioctl(real, EVIOCGBIT(0, sizeof types), types) < 0) { close(real); return -1; }
  ioctl(real, EVIOCGBIT(EV_KEY, sizeof keys), keys);
  ioctl(real, EVIOCGBIT(EV_ABS, sizeof axes), axes);
  ioctl(real, EVIOCGPROP(sizeof props), props);
  int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
  if (fd < 0) { close(real); return -1; }
  int ok = 1;
  // Only key and axis events, never anything else the real device can do (lights, sounds, force feedback).
  ok &= ioctl(fd, UI_SET_EVBIT, EV_SYN) >= 0;
  if (TEST_BIT(types, EV_KEY)) {
    ok &= ioctl(fd, UI_SET_EVBIT, EV_KEY) >= 0;
    for (int k = 0; k < KEY_MAX; k++) if (TEST_BIT(keys, k)) ok &= ioctl(fd, UI_SET_KEYBIT, k) >= 0;
  }
  if (TEST_BIT(types, EV_ABS)) {
    ok &= ioctl(fd, UI_SET_EVBIT, EV_ABS) >= 0;
    for (int a = 0; a < ABS_MAX; a++) {
      if (!TEST_BIT(axes, a)) continue;
      struct uinput_abs_setup setup;
      memset(&setup, 0, sizeof setup);
      setup.code = (unsigned short)a;
      ok &= ioctl(real, EVIOCGABS(a), &setup.absinfo) >= 0;
      ok &= ioctl(fd, UI_SET_ABSBIT, a) >= 0;
      ok &= ioctl(fd, UI_ABS_SETUP, &setup) >= 0;
    }
  }
  for (int p = 0; p < INPUT_PROP_MAX; p++) if (TEST_BIT(props, p)) ok &= ioctl(fd, UI_SET_PROPBIT, p) >= 0;
  // Its location too ("touchPad/input0"): the Titan 2's input reader sets the touchpad's height by it, and without it the
  // twin's heights come out upside down and squeezed.
  char phys[128] = {0};
  if (ioctl(real, EVIOCGPHYS(sizeof phys - 1), phys) >= 0 && phys[0]) ok &= ioctl(fd, UI_SET_PHYS, phys) >= 0;
  close(real);
  struct uinput_setup setup;
  memset(&setup, 0, sizeof setup);
  setup.id = id;
  snprintf(setup.name, sizeof setup.name, "%s", name);
  ok &= ioctl(fd, UI_DEV_SETUP, &setup) >= 0;
  ok &= ioctl(fd, UI_DEV_CREATE) >= 0;
  if (!ok) { close(fd); return -1; }
  return fd;
}

static int fail(int line, const char *what) {
  fprintf(stderr, "line %d: %s\n", line, what);
  return 2;
}

int main(int argc, char **argv) {
  int dry = argc == 3 && strcmp(argv[1], "--dry-run") == 0;
  if (argc != 2 + dry) {
    fprintf(stderr, "usage: tk-inject [--dry-run] SCRIPT\n");
    return 2;
  }
  FILE *in = strcmp(argv[1 + dry], "-") == 0 ? stdin : fopen(argv[1 + dry], "r");
  if (!in) { perror(argv[1 + dry]); return 2; }
  Event *events = calloc(MAX_EVENTS, sizeof *events);
  if (!events) return 2;
  int count = 0, line_no = 0, declared[MAX_DEVICES] = {0};
  char line[512];
  double last = 0;
  while (fgets(line, sizeof line, in)) {
    line_no++;
    char *text = line;
    while (*text == ' ' || *text == '\t') text++;
    if (*text == '#' || *text == '\n' || *text == '\r' || *text == 0) continue;
    if (strncmp(text, "dev ", 4) == 0) {
      int n; char name[64];
      if (sscanf(text + 4, "%d %63s", &n, name) != 2 || n < 0 || n >= MAX_DEVICES) return fail(line_no, "bad dev line");
      if (!allowed(name)) return fail(line_no, "only touchPad and TitanKey may be written");
      if (declared[n]) return fail(line_no, "device declared twice");
      declared[n] = 1;
      snprintf(names[n], sizeof names[n], "%s", name);
      continue;
    }
    Event e;
    if (sscanf(text, "%lf %d %d %d %d", &e.ms, &e.dev, &e.type, &e.code, &e.value) != 5) return fail(line_no, "bad event line");
    if (e.dev < 0 || e.dev >= MAX_DEVICES || !declared[e.dev]) return fail(line_no, "event for an undeclared device");
    if (e.ms < last || e.ms < 0) return fail(line_no, "time goes back");
    if (e.ms > MAX_MILLIS) return fail(line_no, "script longer than 60 s");
    if (e.type != EV_SYN && e.type != EV_KEY && e.type != EV_ABS) return fail(line_no, "only SYN, KEY and ABS events");
    if (e.code < 0 || e.code >= MAX_KEYS) return fail(line_no, "bad code");
    if (e.type == EV_KEY && forbidden(e.code)) return fail(line_no, "power, home, back, app switch and volume keys are never sent");
    if (count >= MAX_EVENTS) return fail(line_no, "too many events");
    last = e.ms;
    events[count++] = e;
  }
  if (in != stdin) fclose(in);
  if (dry) {
    for (int d = 0; d < MAX_DEVICES; d++) if (declared[d]) printf("dev %d %s\n", d, names[d]);
    for (int i = 0; i < count; i++)
      printf("%.3f %d %d %d %d\n", events[i].ms, events[i].dev, events[i].type, events[i].code, events[i].value);
    fprintf(stderr, "%d events, %.1f ms\n", count, count ? events[count - 1].ms : 0.0);
    return 0;
  }
  struct sigaction action;
  memset(&action, 0, sizeof action);
  action.sa_handler = on_signal;
  sigaction(SIGINT, &action, NULL);
  sigaction(SIGTERM, &action, NULL);
  sigaction(SIGHUP, &action, NULL);
  signal(SIGPIPE, SIG_IGN);
  int twins = 0;
  for (int d = 0; d < MAX_DEVICES; d++) {
    if (!declared[d]) continue;
    fds[d] = open_named(names[d], O_RDWR);
    if (fds[d] < 0) {
      fds[d] = open_twin(names[d]);
      twins += fds[d] >= 0;
    }
    if (fds[d] < 0) {
      fprintf(stderr, "no input device called %s that can be written, nor a twin of it through /dev/uinput\n", names[d]);
      release_all();
      return 3;
    }
  }
  // Android takes a moment to add a new device and load its configuration; events before that would be lost.
  if (twins) {
    struct timespec settle = {0, 800 * 1000000L};
    nanosleep(&settle, NULL);
  }
  struct timespec start, now;
  clock_gettime(CLOCK_MONOTONIC, &start);
  double late_most = 0;
  int status = 0;
  for (int i = 0; i < count && !stop; i++) {
    double at = events[i].ms;
    struct timespec when = start;
    long long ns = (long long)(at * 1e6) + when.tv_nsec;
    when.tv_sec += ns / 1000000000LL;
    when.tv_nsec = ns % 1000000000LL;
    while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &when, NULL) == EINTR && !stop) {}
    if (stop) break;
    clock_gettime(CLOCK_MONOTONIC, &now);
    double late = (now.tv_sec - when.tv_sec) * 1e3 + (now.tv_nsec - when.tv_nsec) / 1e6;
    if (late > late_most) late_most = late;
    if (write_event(events[i].dev, events[i].type, events[i].code, events[i].value) < 0) {
      fprintf(stderr, "writing to %s failed: %s\n", names[events[i].dev], strerror(errno));
      status = 4;
      break;
    }
  }
  release_all();
  // A twin goes when its file closes: after a moment, so Android reads the last events from it first.
  if (twins) {
    struct timespec drain = {0, 150 * 1000000L};
    nanosleep(&drain, NULL);
    for (int d = 0; d < MAX_DEVICES; d++) if (fds[d] >= 0) { ioctl(fds[d], UI_DEV_DESTROY); close(fds[d]); }
  }
  if (stop) { fprintf(stderr, "stopped\n"); status = 130; }
  printf("played %d events, %.1f ms, at most %.2f ms late%s\n", count, count ? events[count - 1].ms : 0.0, late_most,
         twins ? ", through uinput twins" : "");
  return status;
}
