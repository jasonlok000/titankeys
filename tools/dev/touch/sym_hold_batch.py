"""The two Sym-hold interactions after B's fix (main ba55a1f7), for checks.py --batch sym-hold: a held Sym switches with a
Yale code pending, and with the Sym panel open (interactions_batch's mixed checks, run on their own)."""
import interactions_batch as ib


def check_code_pending():
    ib.mixed("mixed-code-pending", ["yue-yale", "en"], [*ib.keys("neihou"), "+500"], "en", "th")


def check_sym_panel():
    ib.mixed("mixed-sym-panel", ["ja", "ko"], ["key:sym", "+700"], "ko", "rk")


BATCH = [check_code_pending, check_sym_panel]
finish = ib.finish
