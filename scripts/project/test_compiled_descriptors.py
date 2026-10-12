"""Counterexamples for batched capture: never omit, reorder, relabel or normalize descriptors."""
import subprocess
import unittest
from compiled_descriptors import public_descriptors

def block(name):
    return 'Compiled from "Fixture.java"\npublic interface '+name+' {\n  public abstract long value();\n    descriptor: ()J\n}\n'

class CompiledDescriptors(unittest.TestCase):
    def test_every_class_and_byte_survives_with_bounded_process_count(self):
        paths=['fixture/Kind'+str(i)+'.class' for i in range(129)];calls=[]
        def capture(command):
            calls.append(command);return ''.join(block(name) for name in command[command.index('-s')+1:])
        actual=public_descriptors(paths,'explicit-classpath',capture)
        self.assertEqual({p:block(p[:-6].replace('/','.')) for p in paths},actual)
        self.assertEqual(3,len(calls))
        self.assertEqual([64,64,1],[len(c[c.index('-s')+1:]) for c in calls])
        self.assertTrue(all(c[:6]==['javap','-J-XX:-UsePerfData','-classpath','explicit-classpath','-public','-s'] for c in calls))

    def test_nested_private_record_and_generic_headers_preserve_original_bytes(self):
        paths=['fixture/Owner$Nested.class','fixture/Record.class'];blocks=[
            'Compiled from "Owner.java"\nfinal class fixture.Owner$Nested<T extends java.lang.Object> {\n}\n',
            'Compiled from "Record.java"\npublic final class fixture.Record extends java.lang.Record {\n}\n']
        self.assertEqual(dict(zip(paths,blocks)),public_descriptors(paths,'cp',lambda args:''.join(blocks)))

    def test_missing_extra_reordered_foreign_and_preamble_blocks_are_rejected(self):
        paths=['fixture/A.class','fixture/B.class'];a=block('fixture.A');b=block('fixture.B')
        for malformed in (a,a+b+block('fixture.C'),b+a,a+block('fixture.Foreign'),'unexpected diagnostic\n'+a+b,
                          a+b.replace('Compiled from "Fixture.java"\n',''),a+b.replace('public interface fixture.B','bad declaration fixture.B')):
            with self.subTest(output=malformed),self.assertRaises(ValueError):
                public_descriptors(paths,'cp',lambda args:malformed)

    def test_invalid_inventory_and_batch_size_never_silently_skip_work(self):
        for paths in ([],['fixture/A.class','fixture/A.class'],['../A.class'],['fixture/A'],['-Joption.class']):
            with self.subTest(paths=paths),self.assertRaises(ValueError):public_descriptors(paths,'cp',lambda args:'')
        for size in (0,-1,65,True,1.5):
            with self.subTest(size=size),self.assertRaises(ValueError):public_descriptors(['fixture/A.class'],'cp',lambda args:block('fixture.A'),size)

    def test_capture_failure_is_not_converted_to_partial_success(self):
        failure=subprocess.CalledProcessError(7,['javap'])
        def denied(args):raise failure
        with self.assertRaises(subprocess.CalledProcessError) as actual:public_descriptors(['fixture/A.class'],'cp',denied)
        self.assertIs(failure,actual.exception)

if __name__=='__main__':unittest.main()
