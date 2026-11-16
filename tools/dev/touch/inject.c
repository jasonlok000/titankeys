// tk-inject: plays a script of input events into the phone's own touchpad and keyboard, with real timing.
//
// adb's shell user may write to /dev/input/event* (group input), and Android reads what is written there as the hardware's own
// events. `sendevent` starts one process per event, far too slow for a swipe; this plays a whole gesture from one file.
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

static int allowed(const char *name) {
  for (size_t i = 0; i < sizeof ALLOWED / sizeof *ALLOWED; i++) if (strcmp(name, ALLOWED[i]) == 0) return 1;
  return 0;
}

// Opens the /dev/input/event* whose name is NAME, for writing. -1 if none is.
static int open_named(const char *name) {
  DIR *dir = opendir("/dev/input");
  if (!dir) return -1;
  struct dirent *entry;
  int found = -1;
  while ((entry = readdir(dir)) && found < 0) {
    if (strncmp(entry->d_name, "event", 5) != 0) continue;
    char path[300], got[128] = {0};
    snprintf(path, sizeof path, "/dev/input/%s", entry->d_name);
    int fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd < 0) continue;
    if (ioctl(fd, EVIOCGNAME(sizeof got - 1), got) >= 0 && strcmp(got, name) == 0) found = fd;
    else close(fd);
  }
  closedir(dir);
  return found;
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
  for (int d = 0; d < MAX_DEVICES; d++) {
    if (!declared[d]) continue;
    fds[d] = open_named(names[d]);
    if (fds[d] < 0) {
      fprintf(stderr, "no input device called %s, or no permission to write it\n", names[d]);
      release_all();
      return 3;
    }
  }
  struct sigaction action;
  memset(&action, 0, sizeof action);
  action.sa_handler = on_signal;
  sigaction(SIGINT, &action, NULL);
  sigaction(SIGTERM, &action, NULL);
  sigaction(SIGHUP, &action, NULL);
  signal(SIGPIPE, SIG_IGN);
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
  if (stop) { fprintf(stderr, "stopped\n"); status = 130; }
  printf("played %d events, %.1f ms, at most %.2f ms late\n", count, count ? events[count - 1].ms : 0.0, late_most);
  return status;
}
