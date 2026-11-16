"""The usual sensor step per touchpad cell (10 columns, 4 rows of the touchpad's height), from raw-log-10 (#52)."""
USUAL = [1.53, 1.90, 1.98, 1.84, 1.77, 1.76, 1.72, 1.63, 1.50, 1.41,
         2.26, 2.89, 2.90, 2.70, 2.53, 2.36, 2.38, 2.27, 1.96, 1.29,
         2.26, 3.10, 2.98, 3.00, 2.74, 2.52, 2.52, 2.21, 2.26, 2.26,
         2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26, 2.26]


def usual(x, y_pad):
    return USUAL[min(3, max(0, int(y_pad * 4))) * 10 + min(9, max(0, int(x * 10)))]
